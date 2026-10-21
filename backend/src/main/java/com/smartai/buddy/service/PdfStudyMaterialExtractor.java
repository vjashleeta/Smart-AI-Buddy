package com.smartai.buddy.service;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class PdfStudyMaterialExtractor {

	private static final int MAX_SAFE_PDF_PAGES = 300;
	private static final int MAX_SAFE_PDF_BYTES = 20 * 1024 * 1024;
	private static final int MAX_EXTRACTED_CHARACTERS = 12_000;

	private final int maxPdfBytes;
	private final int maxPdfPages;
	private final int maxExtractedCharacters;

	public PdfStudyMaterialExtractor(
		@Value("${smart-ai-buddy.quiz.pdf.max-file-bytes:20971520}") int maxPdfBytes,
		@Value("${smart-ai-buddy.quiz.pdf.max-pages:300}") int maxPdfPages,
		@Value("${smart-ai-buddy.quiz.pdf.max-extracted-characters:12000}") int maxExtractedCharacters
	) {
		if (maxPdfBytes < 1 || maxPdfBytes > MAX_SAFE_PDF_BYTES
			|| maxPdfPages < 1 || maxPdfPages > MAX_SAFE_PDF_PAGES
			|| maxExtractedCharacters < 1 || maxExtractedCharacters > MAX_EXTRACTED_CHARACTERS) {
			throw new IllegalArgumentException(
				"PDF limits must be positive and cannot exceed 20 MB, 300 pages, or 12,000 extracted characters."
			);
		}
		this.maxPdfBytes = maxPdfBytes;
		this.maxPdfPages = maxPdfPages;
		this.maxExtractedCharacters = maxExtractedCharacters;
	}

	public String extract(String filename, String contentType, byte[] pdfBytes) {
		if (pdfBytes == null || pdfBytes.length == 0) {
			throw invalidPdf("Choose a non-empty PDF file.");
		}
		if (pdfBytes.length > maxPdfBytes) {
			throw invalidPdf("PDF files must be no larger than " + formatMegabytes(maxPdfBytes) + ".");
		}
		if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".pdf")
			|| !"application/pdf".equalsIgnoreCase(contentType)
			|| !hasPdfSignature(pdfBytes)) {
			throw invalidPdf("Choose a valid PDF file.");
		}

		try (PDDocument document = Loader.loadPDF(pdfBytes)) {
			if (document.getNumberOfPages() == 0) {
				throw unreadablePdf();
			}
			if (document.getNumberOfPages() > maxPdfPages) {
				throw invalidPdf("PDF files must contain no more than " + maxPdfPages + " pages.");
			}
			LimitedTextWriter output = new LimitedTextWriter(maxExtractedCharacters);
			new PDFTextStripper().writeText(document, output);
			String text = output.toString().strip();
			if (text.isBlank()) {
				throw unreadablePdf();
			}
			if (text.length() > maxExtractedCharacters) {
				throw invalidPdf(
					"The PDF contains too much text. Use a PDF with no more than "
						+ String.format(Locale.ROOT, "%,d", maxExtractedCharacters)
						+ " extracted characters."
				);
			}
			return text;
		} catch (ResponseStatusException exception) {
			throw exception;
		} catch (ExtractedTextLimitException exception) {
			throw invalidPdf(extractedTextLimitMessage());
		} catch (IOException | IllegalArgumentException exception) {
			throw invalidPdf("The PDF could not be read. Check that it is a valid, unlocked PDF.");
		}
	}

	public int getMaxPdfBytes() {
		return maxPdfBytes;
	}

	public int getMaxPdfPages() {
		return maxPdfPages;
	}

	public int getMaxExtractedCharacters() {
		return maxExtractedCharacters;
	}

	private static String formatMegabytes(int bytes) {
		double megabytes = bytes / (1024d * 1024d);
		return megabytes == Math.floor(megabytes)
			? String.format(Locale.ROOT, "%.0f MB", megabytes)
			: String.format(Locale.ROOT, "%.1f MB", megabytes);
	}

	private String extractedTextLimitMessage() {
		return "The PDF contains too much text. Use a PDF with no more than "
			+ String.format(Locale.ROOT, "%,d", maxExtractedCharacters)
			+ " extracted characters.";
	}

	private static boolean hasPdfSignature(byte[] bytes) {
		byte[] signature = "%PDF-".getBytes(StandardCharsets.US_ASCII);
		if (bytes.length < signature.length) {
			return false;
		}
		for (int index = 0; index < signature.length; index++) {
			if (bytes[index] != signature[index]) {
				return false;
			}
		}
		return true;
	}

	private static ResponseStatusException unreadablePdf() {
		return new ResponseStatusException(
			HttpStatus.BAD_REQUEST,
			"No readable text was found in this PDF. Scanned or image-only PDFs are not supported yet."
		);
	}

	private static ResponseStatusException invalidPdf(String message) {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
	}

	private static final class LimitedTextWriter extends Writer {
		private final int maxCharacters;
		private final StringBuilder text = new StringBuilder();
		private final StringBuilder pendingWhitespace = new StringBuilder();
		private boolean pendingWhitespaceExceeded;

		private LimitedTextWriter(int maxCharacters) {
			this.maxCharacters = maxCharacters;
		}

		@Override
		public void write(char[] characters, int offset, int length) throws IOException {
			for (int index = offset; index < offset + length; index++) {
				char character = characters[index];
				if (Character.isWhitespace(character)) {
					if (!text.isEmpty()) {
						if (text.length() + pendingWhitespace.length() < maxCharacters) {
							pendingWhitespace.append(character);
						} else {
							pendingWhitespaceExceeded = true;
						}
					}
				} else {
					if (pendingWhitespaceExceeded
						|| text.length() + pendingWhitespace.length() + 1 > maxCharacters) {
						throw new ExtractedTextLimitException();
					}
					text.append(pendingWhitespace);
					pendingWhitespace.setLength(0);
					text.append(character);
				}
			}
		}

		@Override
		public void flush() {
		}

		@Override
		public void close() {
		}

		@Override
		public String toString() {
			return text.toString();
		}
	}

	private static final class ExtractedTextLimitException extends IOException {
		private ExtractedTextLimitException() {
			super("Extracted PDF text exceeded its configured limit.");
		}
	}
}
