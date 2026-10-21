(() => {
  const menuButton = document.querySelector('[data-menu-toggle]');
  const sidebar = document.querySelector('.sidebar');
  const scrim = document.querySelector('.mobile-scrim');
  const toast = document.querySelector('[data-toast]');
  const apiHost = window.location.hostname === '127.0.0.1' ? '127.0.0.1' : 'localhost';
  const apiBase = `http://${apiHost}:8080`;
  let toastTimer;

  const syncNavigationAccessibility = () => {
    if (!sidebar) return;
    const isMobile = window.matchMedia('(max-width: 760px)').matches;
    if (!isMobile) {
      sidebar.classList.remove('is-open');
      scrim?.classList.remove('is-visible');
      menuButton?.setAttribute('aria-expanded', 'false');
      menuButton?.setAttribute('aria-label', 'Open navigation');
    }
    const isClosed = !sidebar.classList.contains('is-open');
    const isHidden = isMobile && isClosed;
    sidebar.inert = isHidden;
    if (isHidden) sidebar.setAttribute('aria-hidden', 'true');
    else sidebar.removeAttribute('aria-hidden');
    document.body.classList.toggle('nav-open', isMobile && !isClosed);
  };

  const closeMenu = () => {
    sidebar?.classList.remove('is-open');
    scrim?.classList.remove('is-visible');
    menuButton?.setAttribute('aria-expanded', 'false');
    menuButton?.setAttribute('aria-label', 'Open navigation');
    syncNavigationAccessibility();
  };

  menuButton?.addEventListener('click', () => {
    const isOpen = sidebar?.classList.toggle('is-open') ?? false;
    scrim?.classList.toggle('is-visible', isOpen);
    menuButton.setAttribute('aria-expanded', String(isOpen));
    menuButton.setAttribute('aria-label', isOpen ? 'Close navigation' : 'Open navigation');
    syncNavigationAccessibility();
  });
  scrim?.addEventListener('click', closeMenu);
  sidebar?.querySelectorAll('a').forEach((link) => link.addEventListener('click', closeMenu));
  window.addEventListener('resize', syncNavigationAccessibility);
  syncNavigationAccessibility();
  document.addEventListener('keydown', (event) => {
    if (event.key === 'Escape' && sidebar?.classList.contains('is-open')) closeMenu();
  });

  const dateElement = document.querySelector('[data-current-date]');
  if (dateElement) {
    dateElement.textContent = new Intl.DateTimeFormat('en', { weekday: 'short', month: 'short', day: 'numeric' }).format(new Date());
  }
  const headingDate = document.querySelector('[data-current-day-heading]');
  if (headingDate) {
    const today = new Intl.DateTimeFormat('en', { weekday: 'long', month: 'long', day: 'numeric' }).format(new Date()).toUpperCase();
    headingDate.textContent = `TODAY · ${today}`;
  }

  const formatPercent = (value) => value === null || value === undefined
    ? '—'
    : `${Number(value).toLocaleString(undefined, { maximumFractionDigits: 1 })}%`;
  const formatActivityDate = (value) => {
    if (!value) return 'Date unavailable';
    const date = new Date(value);
    return Number.isNaN(date.getTime())
      ? 'Date unavailable'
      : new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' }).format(date);
  };
  const fetchAnalytics = async (path) => {
    const response = await fetch(`${apiBase}${path}`, { credentials: 'include' });
    const result = await response.json().catch(() => null);
    if (!response.ok) throw new Error(result?.message || 'Unable to load your saved learning activity.');
    return result;
  };
  const createEmptyActivity = (message) => {
    const empty = document.createElement('p');
    empty.className = 'muted';
    empty.textContent = message;
    return empty;
  };
  const createPlannedSessionRow = (session) => {
    const row = document.createElement('div');
    row.className = 'list-row';
    const icon = document.createElement('span');
    icon.className = 'list-icon';
    const subjectName = typeof session.subject === 'string' && session.subject.trim()
      ? session.subject.trim()
      : 'Study session';
    icon.textContent = subjectName.slice(0, 2).toUpperCase();
    const copy = document.createElement('div');
    copy.className = 'list-copy';
    const subject = document.createElement('strong');
    subject.textContent = subjectName;
    const detail = document.createElement('span');
    detail.textContent = session.sessionDate
      ? new Intl.DateTimeFormat(undefined, { dateStyle: 'medium' }).format(new Date(`${session.sessionDate}T12:00:00`))
      : 'Date unavailable';
    copy.append(subject, detail);
    const duration = document.createElement('span');
    duration.className = 'pill';
    duration.textContent = Number.isFinite(session.durationMinutes)
      ? `${session.durationMinutes} min planned`
      : 'Planned duration unavailable';
    row.append(icon, copy, duration);
    return row;
  };
  const formatStudyDuration = (minutes) => {
    const safeMinutes = Number.isFinite(minutes) && minutes >= 0 ? Math.floor(minutes) : 0;
    const hours = Math.floor(safeMinutes / 60);
    const remainingMinutes = safeMinutes % 60;
    return hours > 0 ? `${hours}h ${remainingMinutes}m` : `${remainingMinutes}m`;
  };
  const renderWeeklyActivityChart = (bars, labels, activity) => {
    const days = Array.isArray(activity) ? activity : [];
    if (days.length === 0) {
      bars.replaceChildren();
      labels.replaceChildren();
      return;
    }
    const maxMinutes = Math.max(1, ...days.map((day) => Number(day.minutes) || 0));
    bars.replaceChildren(...days.map((day) => {
      const bar = document.createElement('span');
      const minutes = Number.isFinite(day.minutes) ? day.minutes : 0;
      bar.style.height = `${minutes === 0 ? 5 : Math.max(8, (minutes / maxMinutes) * 100)}%`;
      bar.title = `${day.date}: ${minutes} recorded minutes`;
      bar.setAttribute('aria-label', bar.title);
      return bar;
    }));
    labels.replaceChildren(...days.map((day) => {
      const label = document.createElement('span');
      const date = new Date(`${day.date}T12:00:00`);
      label.textContent = Number.isNaN(date.getTime())
        ? '—'
        : new Intl.DateTimeFormat(undefined, { weekday: 'narrow' }).format(date);
      label.title = day.date;
      return label;
    }));
  };

  const dashboardPage = document.querySelector('[data-dashboard-page]');
  if (dashboardPage) {
    const status = dashboardPage.querySelector('[data-dashboard-status]');
    const quizList = dashboardPage.querySelector('[data-dashboard-quizzes]');
    const sessionList = dashboardPage.querySelector('[data-dashboard-sessions]');
    const setMetric = (selector, value) => {
      dashboardPage.querySelector(selector).textContent = value;
    };
    const loadDashboard = async () => {
      try {
        const data = await fetchAnalytics('/api/student/dashboard');
        const name = typeof data.studentName === 'string' ? data.studentName.trim().split(/\s+/)[0] : '';
        if (name) dashboardPage.querySelector('[data-dashboard-welcome]').textContent = `Welcome back, ${name}`;
        setMetric('[data-dashboard-attempts]', String(data.completedQuizAttempts));
        setMetric('[data-dashboard-average]', formatPercent(data.averageScorePercent));
        setMetric('[data-dashboard-latest]', formatPercent(data.latestScorePercent));
        setMetric(
          '[data-dashboard-latest-detail]',
          data.latestCorrectAnswers === null || data.latestCorrectAnswers === undefined
            || data.latestTotalQuestions === null || data.latestTotalQuestions === undefined
            ? 'Complete a quiz to see your latest score.'
            : `Latest result: ${data.latestCorrectAnswers} of ${data.latestTotalQuestions} marks.`
        );
        if (!data.recentQuizAttempts.length) {
          const empty = createEmptyActivity('Complete your first quiz to see your progress.');
          const link = document.createElement('a');
          link.className = 'text-link';
          link.href = 'quiz.html';
          link.textContent = 'Start a quiz';
          quizList.replaceChildren(empty, link);
        } else {
          quizList.replaceChildren(...data.recentQuizAttempts.map((attempt) => {
            const row = document.createElement('div');
            row.className = 'list-row';
            const icon = document.createElement('span');
            icon.className = 'list-icon';
            icon.textContent = 'Q';
            const copy = document.createElement('div');
            copy.className = 'list-copy';
            const title = document.createElement('strong');
            title.textContent = attempt.topic || 'Quiz';
            const detail = document.createElement('span');
            detail.textContent = `${formatActivityDate(attempt.completedAt)} · ${attempt.correctAnswers}/${attempt.totalQuestions} marks`;
            copy.append(title, detail);
            const score = document.createElement('span');
            score.className = 'pill';
            score.textContent = formatPercent(attempt.scorePercent);
            row.append(icon, copy, score);
            return row;
          }));
        }
        setMetric(
          '[data-dashboard-planned-count]',
          `${data.upcomingPlannedSessionCount} upcoming planned session${data.upcomingPlannedSessionCount === 1 ? '' : 's'}`
        );
        sessionList.replaceChildren(...(data.upcomingPlannedSessions.length
          ? data.upcomingPlannedSessions.map(createPlannedSessionRow)
          : [createEmptyActivity('No upcoming planned sessions.')]));
        status.textContent = '';
        status.hidden = true;
      } catch (error) {
        status.textContent = error.message;
        status.classList.add('is-error');
        setMetric('[data-dashboard-attempts]', '—');
        setMetric('[data-dashboard-average]', '—');
        setMetric('[data-dashboard-latest]', '—');
        quizList.replaceChildren(createEmptyActivity('Your quiz activity could not be loaded.'));
        sessionList.replaceChildren(createEmptyActivity('Your study plan could not be loaded.'));
      }
    };
    loadDashboard();
  }

  const overviewPage = document.querySelector('.page-content [data-overview-status]')?.closest('.page-content');
  if (overviewPage) {
    const status = overviewPage.querySelector('[data-overview-status]');
    const loadOverview = async () => {
      try {
        const data = await fetchAnalytics('/api/student/dashboard');
        const weeklyMinutes = Number.isFinite(data.completedStudyMinutes) ? data.completedStudyMinutes : 0;
        const goalMinutes = Number.isFinite(data.weeklyGoalMinutes) && data.weeklyGoalMinutes > 0
          ? data.weeklyGoalMinutes
          : 360;
        const streakDays = Number.isFinite(data.currentStreakDays) ? data.currentStreakDays : 0;
        overviewPage.querySelector('[data-overview-time]').textContent = formatStudyDuration(weeklyMinutes);
        overviewPage.querySelector('[data-overview-goal-label]').textContent = `(${formatStudyDuration(goalMinutes)})`;
        overviewPage.querySelector('[data-overview-weekly-goal]').textContent =
          `${formatStudyDuration(weeklyMinutes)} of ${formatStudyDuration(goalMinutes)}`;
        overviewPage.querySelector('[data-overview-goal-progress]').style.width =
          `${Math.min(100, Math.max(0, (weeklyMinutes / goalMinutes) * 100))}%`;
        overviewPage.querySelector('[data-overview-streak]').textContent =
          `Current study streak: ${streakDays} consecutive day${streakDays === 1 ? '' : 's'}`;

        const upcoming = data.upcomingPlannedSessions || [];
        const focusText = overviewPage.querySelector('[data-overview-focus-text]');
        focusText.textContent = weeklyMinutes === 0
          ? 'No recorded learning time yet this week. Start with a short practice or complete a planned session.'
          : `${upcoming.length} upcoming planned session${upcoming.length === 1 ? '' : 's'} · ${formatStudyDuration(weeklyMinutes)} of measured learning time this week.`;
        const upcomingList = overviewPage.querySelector('[data-overview-upcoming]');
        upcomingList.replaceChildren(...(upcoming.length
          ? upcoming.slice(0, 3).map(createPlannedSessionRow)
          : [createEmptyActivity('No upcoming planned sessions. Add a session in the Study Planner when you are ready.')]));
        renderWeeklyActivityChart(
          overviewPage.querySelector('[data-overview-weekly-bars]'),
          overviewPage.querySelector('[data-overview-weekly-labels]'),
          data.weeklyActivity
        );
        status.textContent = '';
        status.hidden = true;
      } catch (error) {
        status.textContent = `Your study activity could not be loaded: ${error.message}`;
        status.classList.add('is-error');
        status.hidden = false;
        overviewPage.querySelector('[data-overview-time]').textContent = '—';
        overviewPage.querySelector('[data-overview-weekly-goal]').textContent = '— of —';
        overviewPage.querySelector('[data-overview-streak]').textContent = 'Current study streak unavailable.';
        overviewPage.querySelector('[data-overview-focus-text]').textContent = 'Try refreshing after signing in.';
        overviewPage.querySelector('[data-overview-upcoming]').replaceChildren(
          createEmptyActivity('Planned sessions could not be loaded.')
        );
      }
    };
    loadOverview();
  }

  const performancePage = document.querySelector('[data-performance-page]');
  if (performancePage) {
    const status = performancePage.querySelector('[data-performance-status]');
    const historyBody = performancePage.querySelector('[data-performance-history]');
    const topicList = performancePage.querySelector('[data-performance-topics]');
    const sessionList = performancePage.querySelector('[data-performance-sessions]');
    const setMetric = (selector, value) => {
      performancePage.querySelector(selector).textContent = value;
    };
    const loadPerformance = async () => {
      try {
        const data = await fetchAnalytics('/api/student/performance');
        setMetric('[data-performance-attempts]', String(data.completedQuizAttempts));
        setMetric('[data-performance-average]', formatPercent(data.averageScorePercent));
        setMetric('[data-performance-latest]', formatPercent(data.latestScorePercent));
        setMetric('[data-performance-best]', formatPercent(data.bestScorePercent));
        if (!data.scoreHistory.length) {
          const emptyRow = document.createElement('tr');
          const emptyCell = document.createElement('td');
          emptyCell.colSpan = 4;
          emptyCell.className = 'muted';
          emptyCell.textContent = 'Complete your first quiz to build a score history.';
          emptyRow.append(emptyCell);
          historyBody.replaceChildren(emptyRow);
        } else {
          historyBody.replaceChildren(...data.scoreHistory.map((attempt) => {
            const row = document.createElement('tr');
            const date = document.createElement('td');
            date.textContent = formatActivityDate(attempt.completedAt);
            const topic = document.createElement('td');
            topic.textContent = attempt.topic || 'Uncategorized';
            const score = document.createElement('td');
            score.textContent = `${attempt.correctAnswers}/${attempt.totalQuestions}`;
            const percent = document.createElement('td');
            percent.textContent = formatPercent(attempt.scorePercent);
            row.append(date, topic, score, percent);
            return row;
          }));
        }
        const comparison = performancePage.querySelector('[data-performance-comparison]');
        const latest = data.scoreHistory[0];
        const previous = data.scoreHistory[1];
        if (latest?.scorePercent !== null && latest?.scorePercent !== undefined
          && previous?.scorePercent !== null && previous?.scorePercent !== undefined) {
          const difference = latest.scorePercent - previous.scorePercent;
          const direction = difference === 0 ? 'the same as' : `${Math.abs(difference).toLocaleString(undefined, { maximumFractionDigits: 1 })} points ${difference > 0 ? 'higher than' : 'lower than'}`;
          comparison.textContent = `Your latest score is ${direction} the previous quiz. Two attempts are not enough to establish a long-term trend.`;
          comparison.hidden = false;
        } else {
          comparison.hidden = true;
        }
        if (!data.topicPerformance.length) {
          topicList.replaceChildren(createEmptyActivity('Topic summaries will appear after a completed quiz.'));
        } else {
          topicList.replaceChildren(...data.topicPerformance.map((topic) => {
            const row = document.createElement('div');
            row.className = 'list-row';
            const copy = document.createElement('div');
            copy.className = 'list-copy';
            const title = document.createElement('strong');
            title.textContent = topic.topic;
            const detail = document.createElement('span');
            detail.textContent = `${topic.completedAttempts} completed quiz${topic.completedAttempts === 1 ? '' : 'zes'} · average ${formatPercent(topic.averageScorePercent)}`;
            copy.append(title, detail);
            const best = document.createElement('span');
            best.className = 'pill';
            best.textContent = `Best ${formatPercent(topic.bestScorePercent)}`;
            row.append(copy, best);
            return row;
          }));
        }
        performancePage.querySelector('[data-performance-study-time]').textContent =
          `${formatStudyDuration(data.completedStudyMinutes || 0)} recorded this week · ${data.currentStreakDays || 0} consecutive study day${data.currentStreakDays === 1 ? '' : 's'}.`;
        sessionList.replaceChildren(...(data.upcomingPlannedSessions.length
          ? data.upcomingPlannedSessions.map(createPlannedSessionRow)
          : [createEmptyActivity('No upcoming planned sessions.')]));
        status.textContent = '';
        status.hidden = true;
      } catch (error) {
        status.textContent = error.message;
        status.classList.add('is-error');
        setMetric('[data-performance-attempts]', '—');
        setMetric('[data-performance-average]', '—');
        setMetric('[data-performance-latest]', '—');
        setMetric('[data-performance-best]', '—');
        historyBody.replaceChildren();
        topicList.replaceChildren(createEmptyActivity('Your quiz performance could not be loaded.'));
        sessionList.replaceChildren(createEmptyActivity('Your study plan could not be loaded.'));
      }
    };
    loadPerformance();
  }

  const settingsPage = document.querySelector('[data-settings-page]');
  if (settingsPage) {
    const status = settingsPage.querySelector('[data-settings-status]');
    const profileForm = settingsPage.querySelector('[data-settings-profile-form]');
    const profileMessage = settingsPage.querySelector('[data-settings-profile-message]');
    const passwordForm = settingsPage.querySelector('[data-settings-password-form]');
    const passwordMessage = settingsPage.querySelector('[data-settings-password-message]');
    const profileName = profileForm.elements.namedItem('name');
    const profileEmail = profileForm.elements.namedItem('email');
    const setFormMessage = (element, message, isError = false) => {
      element.textContent = message;
      element.classList.toggle('is-error', isError);
    };
    const setSettingsFormsEnabled = (enabled) => {
      profileForm.querySelectorAll('input, button').forEach((element) => {
        element.disabled = !enabled;
      });
      passwordForm.querySelectorAll('input, button').forEach((element) => {
        element.disabled = !enabled;
      });
    };
    const loadProfile = async () => {
      try {
        const profile = await fetchAnalytics('/api/student/settings/profile');
        profileName.value = profile.name;
        profileEmail.value = profile.email;
        setSettingsFormsEnabled(true);
        status.textContent = '';
        status.hidden = true;
      } catch (error) {
        status.textContent = error.message;
        status.classList.add('is-error');
        setFormMessage(profileMessage, 'Account details could not be loaded.', true);
      }
    };
    profileForm.addEventListener('submit', async (event) => {
      event.preventDefault();
      const button = profileForm.querySelector('button[type="submit"]');
      button.disabled = true;
      setFormMessage(profileMessage, '');
      try {
        const csrf = await fetchCsrfToken();
        const response = await fetch(`${apiBase}/api/student/settings/profile`, {
          method: 'PUT',
          credentials: 'include',
          headers: { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token },
          body: JSON.stringify({ name: profileName.value }),
        });
        const result = await response.json().catch(() => null);
        if (!response.ok) {
          throw new Error(result?.errors?.name || result?.message || result?.detail || 'Your profile could not be updated.');
        }
        profileName.value = result.name;
        profileEmail.value = result.email;
        setFormMessage(profileMessage, 'Your display name was saved.');
      } catch (error) {
        setFormMessage(profileMessage, error.message, true);
      } finally {
        button.disabled = false;
      }
    });
    passwordForm.addEventListener('submit', async (event) => {
      event.preventDefault();
      const button = passwordForm.querySelector('button[type="submit"]');
      const currentPassword = passwordForm.elements.namedItem('currentPassword');
      const newPassword = passwordForm.elements.namedItem('newPassword');
      button.disabled = true;
      setFormMessage(passwordMessage, '');
      try {
        const csrf = await fetchCsrfToken();
        const response = await fetch(`${apiBase}/api/student/settings/password`, {
          method: 'POST',
          credentials: 'include',
          headers: { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token },
          body: JSON.stringify({
            currentPassword: currentPassword.value,
            newPassword: newPassword.value,
          }),
        });
        const result = response.status === 204 ? null : await response.json().catch(() => null);
        if (!response.ok) {
          throw new Error(result?.errors?.newPassword || result?.errors?.currentPassword || result?.message || result?.detail
            || 'Your password could not be changed.');
        }
        passwordForm.reset();
        setFormMessage(passwordMessage, 'Your password was changed successfully.');
      } catch (error) {
        setFormMessage(passwordMessage, error.message, true);
      } finally {
        button.disabled = false;
      }
    });
    loadProfile();
  }

  const showToast = (message) => {
    if (!toast) return;
    toast.textContent = message;
    toast.classList.add('is-visible');
    window.clearTimeout(toastTimer);
    toastTimer = window.setTimeout(() => toast.classList.remove('is-visible'), 2600);
  };

  const fetchCsrfToken = async () => {
    const response = await fetch(`${apiBase}/api/auth/csrf`, {
      credentials: 'include',
    });
    if (!response.ok) throw new Error('Unable to prepare a secure request.');
    return response.json();
  };

  const trackedActivityType = document.querySelector('[data-chat-form]')
    ? 'AI_BUDDY'
    : document.querySelector('[data-socratic-tutor]')
      ? 'SOCRATIC_TUTOR'
      : document.querySelector('[data-quiz-page]')
        ? 'QUIZ'
        : null;
  if (trackedActivityType) {
    const trackerStatus = document.querySelector('[data-learning-time-status]');
    let activityId = null;
    let heartbeatTimer = null;
    let lastEngagementAt = 0;
    let requestInProgress = false;
    let csrfToken = null;

    const showTrackerStatus = (text, isError = false) => {
      if (!trackerStatus) return;
      trackerStatus.textContent = text;
      trackerStatus.classList.toggle('is-error', isError);
    };

    const getTrackerCsrf = async () => {
      if (!csrfToken) csrfToken = await fetchCsrfToken();
      return csrfToken;
    };

    const stopActivityTracking = async (keepalive = false) => {
      window.clearInterval(heartbeatTimer);
      heartbeatTimer = null;
      const stoppedActivityId = activityId;
      activityId = null;
      if (stoppedActivityId === null || !csrfToken) return;
      try {
        await fetch(`${apiBase}/api/student/learning-activity/sessions/${encodeURIComponent(stoppedActivityId)}/stop`, {
          method: 'POST',
          credentials: 'include',
          keepalive,
          headers: { [csrfToken.headerName]: csrfToken.token },
        });
      } catch {
        showTrackerStatus('Time tracking could not be paused cleanly. Recorded time is limited to the last active check.', true);
      }
    };

    const sendHeartbeat = async () => {
      if (activityId === null || requestInProgress) return;
      if (document.visibilityState !== 'visible' || Date.now() - lastEngagementAt > 45_000) {
        await stopActivityTracking();
        showTrackerStatus('Active-time tracking is paused while this page is idle or hidden.');
        return;
      }
      requestInProgress = true;
      try {
        const response = await fetch(
          `${apiBase}/api/student/learning-activity/sessions/${encodeURIComponent(activityId)}/heartbeat`,
          {
            method: 'PUT',
            credentials: 'include',
            headers: { [csrfToken.headerName]: csrfToken.token },
          }
        );
        const result = await response.json().catch(() => null);
        if (!response.ok) {
          throw new Error(result?.message || 'Active learning time could not be recorded.');
        }
        if (!result?.active) {
          activityId = null;
          window.clearInterval(heartbeatTimer);
          heartbeatTimer = null;
          showTrackerStatus('Time tracking paused after an inactive period. Interact to resume.');
        }
      } catch (error) {
        await stopActivityTracking();
        showTrackerStatus(error.message, true);
      } finally {
        requestInProgress = false;
      }
    };

    const startActivityTracking = async () => {
      lastEngagementAt = Date.now();
      if (activityId !== null || requestInProgress || document.visibilityState !== 'visible') return;
      requestInProgress = true;
      try {
        csrfToken = await getTrackerCsrf();
        const response = await fetch(`${apiBase}/api/student/learning-activity/sessions`, {
          method: 'POST',
          credentials: 'include',
          headers: {
            'Content-Type': 'application/json',
            [csrfToken.headerName]: csrfToken.token,
          },
          body: JSON.stringify({ activityType: trackedActivityType }),
        });
        const result = await response.json().catch(() => null);
        if (!response.ok || !result?.id) {
          throw new Error(result?.message || 'Active learning time could not be started.');
        }
        activityId = result.id;
        heartbeatTimer = window.setInterval(() => void sendHeartbeat(), 15_000);
        showTrackerStatus('Active time is recorded only while this page is visible and recently in use.');
      } catch (error) {
        showTrackerStatus(error.message, true);
      } finally {
        requestInProgress = false;
      }
    };

    ['click', 'input', 'keydown', 'pointerdown'].forEach((eventName) => {
      document.addEventListener(eventName, () => {
        lastEngagementAt = Date.now();
        if (activityId === null) void startActivityTracking();
      }, { passive: true });
    });
    document.addEventListener('visibilitychange', () => {
      if (document.visibilityState !== 'visible') {
        void stopActivityTracking();
        showTrackerStatus('Active-time tracking is paused while this page is hidden.');
      }
    });
    window.addEventListener('pagehide', () => {
      void stopActivityTracking(true);
    });
  }

  document.querySelector('[data-focus-session-form]')?.addEventListener('click', () => {
    const subjectInput = document.querySelector('[data-planner-form] [name="subject"]');
    subjectInput?.scrollIntoView({ behavior: 'smooth', block: 'center' });
    subjectInput?.focus({ preventScroll: true });
  });

  const plannerForm = document.querySelector('[data-planner-form]');
  if (plannerForm) {
    const sessionList = document.querySelector('[data-planner-sessions]');
    const sessionCount = document.querySelector('[data-planner-count]');
    const message = plannerForm.querySelector('[data-form-message]');
    const submitButton = plannerForm.querySelector('button[type="submit"]');
    const dateInput = plannerForm.elements.namedItem('sessionDate');
    const now = new Date();
    const localToday = [
      now.getFullYear(),
      String(now.getMonth() + 1).padStart(2, '0'),
      String(now.getDate()).padStart(2, '0'),
    ].join('-');
    dateInput.min = localToday;

    const setPlannerMessage = (text, isError = false) => {
      message.textContent = text;
      message.classList.toggle('is-error', isError);
    };

    const setEmptyMessage = (text, isError = false) => {
      const emptyMessage = document.createElement('p');
      emptyMessage.className = 'muted';
      emptyMessage.textContent = text;
      if (isError) emptyMessage.classList.add('is-error');
      sessionList.replaceChildren(emptyMessage);
    };

    const renderPlanner = (planner) => {
      const sessions = planner.sessions || [];
      sessionCount.textContent = `${sessions.length} session${sessions.length === 1 ? '' : 's'}`;
      if (sessions.length === 0) {
        setEmptyMessage('No study sessions saved yet. Add one to start your plan.');
        return;
      }

      const rows = sessions.map((session, index) => {
        const row = document.createElement('div');
        row.className = 'list-row';

        const date = document.createElement('span');
        date.className = 'schedule-time';
        date.textContent = new Intl.DateTimeFormat(undefined, {
          month: 'short',
          day: 'numeric',
          year: 'numeric',
        }).format(new Date(`${session.sessionDate}T00:00:00`));

        const marker = document.createElement('span');
        marker.className = `schedule-marker ${index % 2 === 0 ? 'marker-green' : 'marker-orange'}`;

        const copy = document.createElement('label');
        copy.className = 'list-copy';
        const subject = document.createElement('strong');
        subject.textContent = session.subject;
        const dateText = document.createElement('span');
        dateText.textContent = session.completedAt
          ? `Completed · ${session.actualDurationMinutes} actual minutes of ${session.durationMinutes} planned`
          : session.actualDurationMinutes === null || session.actualDurationMinutes === undefined
            ? `Planned study session · ${session.durationMinutes} minutes`
            : `Actual time recorded · ${session.actualDurationMinutes} minutes of ${session.durationMinutes} planned`;
        copy.append(subject, dateText);

        const duration = document.createElement('span');
        duration.className = 'pill';
        duration.textContent = `${session.durationMinutes} min`;

        row.append(date, marker, copy, duration);
        const actionGroup = document.createElement('div');
        actionGroup.className = 'session-completion-actions';
        const todayString = [
          new Date().getFullYear(),
          String(new Date().getMonth() + 1).padStart(2, '0'),
          String(new Date().getDate()).padStart(2, '0'),
        ].join('-');
        if (session.sessionDate <= todayString) {
          const actualTimeForm = document.createElement('form');
          actualTimeForm.className = 'session-actual-time-form';
          const actualTimeInput = document.createElement('input');
          actualTimeInput.type = 'number';
          actualTimeInput.name = 'actualDurationMinutes';
          actualTimeInput.min = '1';
          actualTimeInput.max = String(session.durationMinutes);
          actualTimeInput.step = '1';
          actualTimeInput.required = true;
          actualTimeInput.value = session.actualDurationMinutes === null || session.actualDurationMinutes === undefined
            ? ''
            : String(session.actualDurationMinutes);
          actualTimeInput.placeholder = 'Actual minutes';
          actualTimeInput.setAttribute('aria-label', `Actual minutes studied for ${session.subject}`);
          const saveActualTime = document.createElement('button');
          saveActualTime.type = 'submit';
          saveActualTime.className = 'button button-light button-small';
          saveActualTime.textContent = session.actualDurationMinutes === null || session.actualDurationMinutes === undefined
            ? 'Record actual time'
            : 'Update time';
          actualTimeForm.append(actualTimeInput, saveActualTime);
          actualTimeForm.addEventListener('submit', async (event) => {
            event.preventDefault();
            if (!actualTimeForm.reportValidity()) return;
            const minutes = Number(actualTimeInput.value);
            if (!Number.isInteger(minutes) || minutes < 1 || minutes > session.durationMinutes) {
              setPlannerMessage(`Enter a whole number of actual minutes from 1 to ${session.durationMinutes}.`, true);
              actualTimeInput.focus();
              return;
            }
            await saveActualDuration(session.id, minutes, saveActualTime, 'Actual study time saved.');
          });
          actionGroup.append(actualTimeForm);

          if (!session.completedAt) {
            const completeButton = document.createElement('button');
            completeButton.type = 'button';
            completeButton.className = 'button button-dark button-small';
            completeButton.textContent = 'Mark completed';
            completeButton.addEventListener('click', () => {
              if (!actualTimeForm.reportValidity()) return;
              const minutes = Number(actualTimeInput.value);
              if (!Number.isInteger(minutes) || minutes < 1 || minutes > session.durationMinutes) {
                setPlannerMessage(`Enter a whole number of actual minutes from 1 to ${session.durationMinutes}.`, true);
                actualTimeInput.focus();
                return;
              }
              void saveActualDuration(
                session.id,
                minutes,
                completeButton,
                'Session completed with the saved actual study time.',
                'complete'
              );
            });
            actionGroup.append(completeButton);
          }
        } else {
          const pending = document.createElement('span');
          pending.className = 'form-note';
          pending.textContent = 'Completion available on the session date.';
          actionGroup.append(pending);
        }
        row.append(actionGroup);
        return row;
      });
      sessionList.replaceChildren(...rows);
    };

    const saveActualDuration = async (
      sessionId,
      actualDurationMinutes,
      button,
      successMessage,
      action = 'actual-time'
    ) => {
      button.disabled = true;
      setPlannerMessage('');
      try {
        const csrf = await fetchCsrfToken();
        const response = await fetch(
          `${apiBase}/api/student/planner/sessions/${encodeURIComponent(sessionId)}/${action}`,
          {
            method: 'PUT',
            credentials: 'include',
            headers: {
              'Content-Type': 'application/json',
              [csrf.headerName]: csrf.token,
            },
            body: JSON.stringify({ actualDurationMinutes }),
          }
        );
        const result = await response.json().catch(() => null);
        if (!response.ok) {
          throw new Error(
            result?.message
              || `Study time could not be saved (HTTP ${response.status}). The session was not confirmed as updated.`
          );
        }
        setPlannerMessage(successMessage);
        try {
          await loadPlanner();
        } catch (error) {
          setPlannerMessage(`Study time was saved, but the session list could not be refreshed: ${error.message}`, true);
        }
      } catch (error) {
        setPlannerMessage(error.message || 'Actual study time could not be saved.', true);
        button.disabled = false;
      }
    };

    const loadPlanner = async () => {
      const response = await fetch(`${apiBase}/api/student/planner`, {
        credentials: 'include',
      });
      if (!response.ok) {
        const result = await response.json().catch(() => null);
        throw new Error(result?.message || 'Unable to load your saved sessions.');
      }
      renderPlanner(await response.json());
    };

    loadPlanner().catch((error) => {
      setEmptyMessage(error.message, true);
    });

    plannerForm.addEventListener('submit', async (event) => {
      event.preventDefault();
      setPlannerMessage('');
      if (submitButton) {
        submitButton.disabled = true;
        submitButton.setAttribute('aria-busy', 'true');
      }

      try {
        const csrf = await fetchCsrfToken();
        const response = await fetch(`${apiBase}/api/student/planner/sessions`, {
          method: 'POST',
          credentials: 'include',
          headers: {
            'Content-Type': 'application/json',
            [csrf.headerName]: csrf.token,
          },
          body: JSON.stringify({
            subject: plannerForm.elements.namedItem('subject').value,
            sessionDate: dateInput.value,
            durationMinutes: Number(plannerForm.elements.namedItem('durationMinutes').value),
          }),
        });

        if (!response.ok) {
          const result = await response.json().catch(() => null);
          setPlannerMessage(result?.message || 'Your session could not be saved. Please try again.', true);
          return;
        }

        plannerForm.reset();
        setPlannerMessage('Session saved.');
        try {
          await loadPlanner();
        } catch {
          setPlannerMessage('Session saved, but the saved list could not be refreshed.', true);
        }
      } catch {
        setPlannerMessage('Unable to save your session. Check your connection and try again.', true);
      } finally {
        if (submitButton) {
          submitButton.disabled = false;
          submitButton.removeAttribute('aria-busy');
        }
      }
    });
  }

  document.querySelectorAll('[data-logout]').forEach((button) => {
    let logoutInProgress = false;

    button.addEventListener('click', async () => {
      if (logoutInProgress) return;
      logoutInProgress = true;
      button.disabled = true;
      button.setAttribute('aria-busy', 'true');

      try {
        const csrf = await fetchCsrfToken();
        const response = await fetch(`${apiBase}/api/auth/logout`, {
          method: 'POST',
          credentials: 'include',
          headers: {
            [csrf.headerName]: csrf.token,
          },
        });

        if (!response.ok) {
          showToast('Sign out failed. Please try again.');
          return;
        }

        window.location.assign('login.html');
      } catch {
        showToast('Unable to sign out. Check your connection and try again.');
      } finally {
        logoutInProgress = false;
        button.disabled = false;
        button.removeAttribute('aria-busy');
      }
    });
  });

  document.querySelectorAll('[data-toast-message]').forEach((button) => {
    button.addEventListener('click', () => showToast(button.dataset.toastMessage || 'This demo action is not connected yet.'));
  });

  document.querySelectorAll('[data-registration-form]').forEach((form) => {
    const message = form.querySelector('[data-form-message]');
    const submitButton = form.querySelector('button[type="submit"]');
    const submitLabel = form.querySelector('[data-submit-label]');
    const passwordInput = form.querySelector('[name="password"]');
    const fieldLabels = { name: 'Name', email: 'College email', password: 'Password' };

    form.addEventListener('submit', async (event) => {
      event.preventDefault();
      if (submitButton?.disabled) return;

      if (message) {
        message.textContent = '';
        message.classList.remove('is-error');
      }
      if (submitButton) {
        submitButton.disabled = true;
        submitButton.setAttribute('aria-busy', 'true');
      }
      if (submitLabel) submitLabel.textContent = 'Creating account...';

      try {
        const csrf = await fetchCsrfToken();
        const response = await fetch(`${apiBase}/api/auth/register`, {
          method: 'POST',
          credentials: 'include',
          headers: {
            'Content-Type': 'application/json',
            [csrf.headerName]: csrf.token,
          },
          body: JSON.stringify({
            name: form.elements.namedItem('name').value,
            email: form.elements.namedItem('email').value,
            password: passwordInput.value,
          }),
        });
        let result;
        try {
          result = await response.json();
        } catch {
          result = null;
        }

        if (!response.ok) {
          const errors = result?.errors && Object.entries(result.errors)
            .map(([field, text]) => `${fieldLabels[field] || 'Registration'}: ${text}`);
          const errorMessage = errors?.length
            ? errors.join(' ')
            : result?.message || 'Registration could not be completed. Please try again.';
          if (message) {
            message.textContent = errorMessage;
            message.classList.add('is-error');
          }
          return;
        }

        if (message) message.textContent = 'Your account was created successfully.';
        form.reset();
      } catch {
        if (message) {
          message.textContent = 'Unable to reach the registration service. Check that the backend is running and try again.';
          message.classList.add('is-error');
        }
      } finally {
        if (submitButton) {
          submitButton.disabled = false;
          submitButton.removeAttribute('aria-busy');
        }
        if (submitLabel) submitLabel.textContent = 'Create account';
      }
    });
  });

  document.querySelectorAll('[data-login-form]').forEach((form) => {
    const message = form.querySelector('[data-form-message]');
    const submitButton = form.querySelector('button[type="submit"]');

    form.addEventListener('submit', async (event) => {
      event.preventDefault();
      if (message) {
        message.textContent = '';
        message.classList.remove('is-error');
      }
      if (submitButton) {
        submitButton.disabled = true;
        submitButton.setAttribute('aria-busy', 'true');
      }

      try {
        const csrf = await fetchCsrfToken();
        const response = await fetch(`${apiBase}/api/auth/login`, {
          method: 'POST',
          credentials: 'include',
          headers: {
            'Content-Type': 'application/json',
            [csrf.headerName]: csrf.token,
          },
          body: JSON.stringify({
            email: form.elements.namedItem('email').value,
            password: form.elements.namedItem('password').value,
          }),
        });

        if (response.ok) {
          window.location.assign(`${apiBase}/pages/dashboard.html`);
          return;
        }

        const result = await response.json().catch(() => null);
        if (message) {
          message.textContent = result?.message || 'Sign in could not be completed. Please try again.';
          message.classList.add('is-error');
        }
      } catch {
        if (message) {
          message.textContent = 'Unable to reach the sign-in service. Check that the backend is running and try again.';
          message.classList.add('is-error');
        }
      } finally {
        if (submitButton) {
          submitButton.disabled = false;
          submitButton.removeAttribute('aria-busy');
        }
      }
    });
  });

  const chatForm = document.querySelector('[data-chat-form]');
  if (chatForm) {
    const chatLog = document.querySelector('[data-chat-log]');
    const chatStatus = document.querySelector('[data-chat-status]');
    const messageInput = chatForm.elements.namedItem('message');
    const sendButton = chatForm.querySelector('button[type="submit"]');
    const conversationSelect = document.querySelector('[data-chat-conversations]');
    const newConversationButton = document.querySelector('[data-new-conversation]');
    const activeConversationStorageKey = 'smart-ai-buddy-active-conversation';
    const storedConversationId = (() => {
      try {
        const storedId = window.localStorage.getItem(activeConversationStorageKey);
        if (!storedId || !/^[1-9]\d*$/.test(storedId)) return null;
        const conversationId = Number(storedId);
        return Number.isSafeInteger(conversationId) ? conversationId : null;
      } catch {
        return null;
      }
    })();
    let activeConversationId = null;
    let conversationList = [];

    const storeActiveConversationId = (conversationId) => {
      try {
        if (conversationId === null) window.localStorage.removeItem(activeConversationStorageKey);
        else window.localStorage.setItem(activeConversationStorageKey, String(conversationId));
      } catch {
        showChatError('Your conversation is available, but this browser could not remember it.');
      }
    };

    const appendChatMessage = (author, text, kind, requestId = null, status = null) => {
      chatLog.querySelector('[data-chat-empty]')?.remove();
      const message = document.createElement('div');
      message.className = `message message-${kind}`;
      if (requestId) message.dataset.requestId = requestId;
      const label = document.createElement('span');
      label.className = 'message-author';
      label.textContent = author;
      const content = document.createElement('p');
      content.textContent = text;
      message.append(label, content);
      if (status) {
        const state = document.createElement('span');
        state.className = 'message-state';
        state.textContent = status;
        message.append(state);
      }
      chatLog.append(message);
      message.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
      return message;
    };

    const showChatError = (message) => {
      chatStatus.textContent = message;
      chatStatus.classList.add('is-error');
    };

    const apiRequest = async (url, method = 'GET', body = null) => {
      const headers = {};
      if (body !== null) headers['Content-Type'] = 'application/json';
      if (method !== 'GET') {
        const csrf = await fetchCsrfToken();
        headers[csrf.headerName] = csrf.token;
      }
      const response = await fetch(`${apiBase}${url}`, {
        method,
        credentials: 'include',
        headers,
        ...(body === null ? {} : { body: JSON.stringify(body) }),
      });
      const result = await response.json().catch(() => null);
      if (!response.ok) {
        const error = new Error(result?.message || 'AI Buddy could not complete this request.');
        error.responseData = result;
        throw error;
      }
      return result;
    };

    const updateConversationOptions = () => {
      conversationSelect.replaceChildren();
      conversationList.forEach((conversation) => {
        const option = document.createElement('option');
        option.value = String(conversation.id);
        option.textContent = conversation.title || 'New conversation';
        conversationSelect.append(option);
      });
      if (!conversationList.length) {
        const option = document.createElement('option');
        option.value = '';
        option.textContent = 'No conversations';
        conversationSelect.append(option);
      }
      conversationSelect.value = activeConversationId === null ? '' : String(activeConversationId);
    };

    const loadConversationMessages = async (conversationId) => {
      chatLog.replaceChildren();
      chatStatus.textContent = 'Loading conversation...';
      chatStatus.classList.remove('is-error');
      try {
        const messages = await apiRequest(
          `/api/student/chat/conversations/${encodeURIComponent(conversationId)}/messages`
        );
        messages.forEach((storedMessage) => {
          const isStudent = storedMessage.role === 'USER';
          const item = appendChatMessage(
            isStudent ? 'YOU' : 'SMART BUDDY',
            storedMessage.content,
            isStudent ? 'student' : 'buddy',
            storedMessage.requestId,
            isStudent && storedMessage.status !== 'COMPLETED'
              ? storedMessage.status === 'PENDING' ? 'Response pending' : 'Response failed'
              : null
          );
          if (isStudent && storedMessage.status !== 'COMPLETED') addRetryButton(item);
        });
        if (!messages.length) {
          const emptyMessage = document.createElement('p');
          emptyMessage.className = 'muted';
          emptyMessage.dataset.chatEmpty = '';
          emptyMessage.textContent = 'Ask AI Buddy a question to get started.';
          chatLog.append(emptyMessage);
        }
        chatStatus.textContent = '';
        return true;
      } catch (error) {
        showChatError(error.message);
        return false;
      }
    };

    const refreshConversationList = async (selectedId = activeConversationId) => {
      conversationList = await apiRequest('/api/student/chat/conversations');
      if (selectedId !== null && conversationList.some((conversation) => conversation.id === Number(selectedId))) {
        activeConversationId = Number(selectedId);
      } else {
        activeConversationId = conversationList[0]?.id ?? null;
      }
      updateConversationOptions();
    };

    const createConversation = async () => {
      const conversation = await apiRequest('/api/student/chat/conversations', 'POST');
      await refreshConversationList(conversation.id);
      storeActiveConversationId(activeConversationId);
      await loadConversationMessages(conversation.id);
      messageInput.focus();
    };

    function addRetryButton(messageElement) {
      if (messageElement.querySelector('[data-retry-message]')) return;
      const retryButton = document.createElement('button');
      retryButton.className = 'chat-retry';
      retryButton.type = 'button';
      retryButton.dataset.retryMessage = '';
      retryButton.textContent = 'Retry response';
      messageElement.append(retryButton);
    }

    const sendQuestion = async (question, requestId, existingQuestionMessage = null) => {
      sendButton.disabled = true;
      sendButton.setAttribute('aria-busy', 'true');
      messageInput.disabled = true;
      chatStatus.textContent = '';
      chatStatus.classList.remove('is-error');
      const questionMessage = existingQuestionMessage
        || appendChatMessage('YOU', question, 'student', requestId, 'Sending...');
      questionMessage.dataset.requestId = requestId;
      questionMessage.querySelector('.message-state').textContent = 'Sending...';
      questionMessage.querySelector('[data-retry-message]')?.remove();
      const pendingMessage = appendChatMessage('SMART BUDDY', 'Thinking...', 'buddy');

      try {
        const result = await apiRequest('/api/student/chat', 'POST', {
          message: question,
          conversationId: activeConversationId,
          requestId,
        });
        if (result?.conversationId) {
          activeConversationId = result.conversationId;
          storeActiveConversationId(activeConversationId);
        }
        if (typeof result?.answer !== 'string' || !result.answer.trim()) {
          pendingMessage.remove();
          questionMessage.querySelector('.message-state').textContent = 'Response failed';
          addRetryButton(questionMessage);
          showChatError(result?.message || 'AI Buddy returned an empty response. Please try again.');
          return;
        }
        pendingMessage.querySelector('p').textContent = result.answer;
        questionMessage.querySelector('.message-state').remove();
        chatForm.reset();
        try {
          await refreshConversationList(activeConversationId);
        } catch {
          showChatError('Your answer was saved, but the conversation list could not be refreshed.');
        }
      } catch (error) {
        pendingMessage.remove();
        if (error.responseData?.conversationId) {
          activeConversationId = error.responseData.conversationId;
        }
        questionMessage.querySelector('.message-state').textContent = error.responseData?.message
          ? 'Response failed'
          : 'Response status unknown';
        addRetryButton(questionMessage);
        showChatError(error.message);
        questionMessage.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
      } finally {
        sendButton.disabled = false;
        sendButton.removeAttribute('aria-busy');
        messageInput.disabled = false;
      }
    };

    chatForm.addEventListener('submit', async (event) => {
      event.preventDefault();
      if (sendButton.disabled) return;
      const question = messageInput.value.trim();
      chatStatus.textContent = '';
      chatStatus.classList.remove('is-error');
      if (!question) {
        showChatError('Enter a question before sending.');
        messageInput.focus();
        return;
      }
      if (activeConversationId === null) {
        showChatError('Create or select a conversation before sending a message.');
        return;
      }
      await sendQuestion(question, crypto.randomUUID());
      messageInput.focus();
    });

    chatLog.addEventListener('click', (event) => {
      const retryButton = event.target.closest('[data-retry-message]');
      if (!retryButton || sendButton.disabled) return;
      const questionMessage = retryButton.closest('.message-student');
      const requestId = questionMessage?.dataset.requestId;
      const question = questionMessage?.querySelector('p')?.textContent;
      if (requestId && question) sendQuestion(question, requestId, questionMessage);
    });

    conversationSelect.addEventListener('change', async () => {
      if (!conversationSelect.value) return;
      activeConversationId = Number(conversationSelect.value);
      storeActiveConversationId(activeConversationId);
      await loadConversationMessages(activeConversationId);
    });

    newConversationButton.addEventListener('click', async () => {
      newConversationButton.disabled = true;
      try {
        await createConversation();
      } catch (error) {
        showChatError(error.message);
      } finally {
        newConversationButton.disabled = false;
      }
    });

    (async () => {
      conversationSelect.disabled = true;
      newConversationButton.disabled = true;
      try {
        await refreshConversationList(storedConversationId ? Number(storedConversationId) : null);
        if (!conversationList.length) {
          await createConversation();
        } else {
          const loaded = await loadConversationMessages(activeConversationId);
          if (!loaded) {
            activeConversationId = null;
            storeActiveConversationId(null);
            updateConversationOptions();
          } else {
            storeActiveConversationId(activeConversationId);
          }
        }
      } catch (error) {
        showChatError(error.message);
        if (storedConversationId) storeActiveConversationId(null);
      } finally {
        conversationSelect.disabled = false;
        newConversationButton.disabled = false;
      }
    })();
  }

  const socraticTutor = document.querySelector('[data-socratic-tutor]');
  if (socraticTutor) {
    const form = socraticTutor.querySelector('[data-socratic-form]');
    const log = socraticTutor.querySelector('[data-socratic-log]');
    const input = form.elements.namedItem('message');
    const status = socraticTutor.querySelector('[data-socratic-status]');
    const sendButton = socraticTutor.querySelector('[data-socratic-send]');
    const hintButton = socraticTutor.querySelector('[data-socratic-hint]');
    const resetButton = socraticTutor.querySelector('[data-socratic-reset]');
    const history = [];
    let started = false;

    const appendTutorMessage = (author, text, kind) => {
      log.querySelector('[data-socratic-empty]')?.remove();
      const message = document.createElement('div');
      message.className = `message message-${kind}`;
      const label = document.createElement('span');
      label.className = 'message-author';
      label.textContent = author;
      const content = document.createElement('p');
      content.textContent = text;
      message.append(label, content);
      log.append(message);
      message.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
      return message;
    };

    const setTutorBusy = (busy) => {
      input.disabled = busy;
      sendButton.disabled = busy;
      hintButton.disabled = busy || !started;
      resetButton.disabled = busy;
      sendButton.setAttribute('aria-busy', String(busy));
      sendButton.textContent = busy ? 'Thinking...' : started ? 'Send response →' : 'Start learning →';
    };

    const askTutor = async (message, clearInput = false) => {
      const question = message.trim();
      status.textContent = '';
      status.classList.remove('is-error');
      if (!question) {
        status.textContent = 'Enter a topic, question, or response before sending.';
        status.classList.add('is-error');
        input.focus();
        return;
      }

      setTutorBusy(true);
      const studentMessage = appendTutorMessage('YOU', question, 'student');
      const pendingMessage = appendTutorMessage('SOCRATIC TUTOR', 'Thinking...', 'buddy');
      status.textContent = 'The tutor is thinking through your response...';

      try {
        const csrf = await fetchCsrfToken();
        const response = await fetch(`${apiBase}/api/student/socratic`, {
          method: 'POST',
          credentials: 'include',
          headers: {
            'Content-Type': 'application/json',
            [csrf.headerName]: csrf.token,
          },
          body: JSON.stringify({ message: question, history: history.slice(-20) }),
        });
        const result = await response.json().catch(() => null);
        if (!response.ok) {
          throw new Error(result?.message || 'The Socratic tutor could not respond. Please try again.');
        }
        if (typeof result?.answer !== 'string' || !result.answer.trim()) {
          throw new Error('The Socratic tutor returned an empty response. Please try again.');
        }

        pendingMessage.querySelector('p').textContent = result.answer;
        history.push(
          { role: 'user', content: question },
          { role: 'model', content: result.answer }
        );
        started = true;
        if (clearInput) input.value = '';
        status.textContent = 'Your turn: share your reasoning or ask for a hint.';
      } catch (error) {
        studentMessage.remove();
        pendingMessage.remove();
        status.textContent = error.message;
        status.classList.add('is-error');
      } finally {
        setTutorBusy(false);
        if (!clearInput) input.focus();
      }
    };

    form.addEventListener('submit', (event) => {
      event.preventDefault();
      askTutor(input.value, true);
    });

    hintButton.addEventListener('click', () => {
      askTutor('I would like a small hint, please do not reveal the full solution.');
    });

    resetButton.addEventListener('click', () => {
      history.length = 0;
      started = false;
      log.replaceChildren();
      const emptyMessage = document.createElement('p');
      emptyMessage.className = 'muted';
      emptyMessage.dataset.socraticEmpty = '';
      emptyMessage.textContent = 'What would you like to understand? Share a topic or question to begin.';
      log.append(emptyMessage);
      input.value = '';
      status.textContent = '';
      status.classList.remove('is-error');
      setTutorBusy(false);
      input.focus();
    });
  }

  const resourcePage = document.querySelector('[data-resource-page]');
  if (resourcePage) {
    const weakTopicList = resourcePage.querySelector('[data-weak-topic-recommendations]');
    const reportedTopicList = resourcePage.querySelector('[data-reported-topic-recommendations]');
    const recentResourcesList = resourcePage.querySelector('[data-recent-resources]');
    const difficultTopicForm = resourcePage.querySelector('[data-difficult-topic-form]');
    const difficultTopicMessage = resourcePage.querySelector('[data-difficult-topic-message]');
    const difficultTopicInput = difficultTopicForm.elements.namedItem('topic');
    const resourcePdfForm = resourcePage.querySelector('[data-resource-pdf-form]');
    const resourcePdfMessage = resourcePage.querySelector('[data-resource-pdf-message]');
    const resourcePdfLimits = resourcePage.querySelector('[data-resource-pdf-limits]');
    const resourcePdfRecommendations = resourcePage.querySelector('[data-resource-pdf-recommendations]');

    const requestResourcePageData = async (url, failureMessage, options = {}) => {
      const response = await fetch(`${apiBase}${url}`, {
        credentials: 'include',
        ...options,
      });
      const result = await response.json().catch(() => null);
      if (!response.ok) {
        throw new Error(result?.message || failureMessage);
      }
      return result;
    };

    const renderTopicRecommendation = (recommendation) => {
      const section = document.createElement('section');
      section.className = 'section-block';
      const heading = document.createElement('h3');
      heading.textContent = recommendation.topic;
      const explanation = document.createElement('p');
      explanation.className = 'muted';
      explanation.textContent = recommendation.explanation;
      section.append(heading, explanation);

      if (recommendation.resources.length === 0) {
        return section;
      }
      const resources = document.createElement('div');
      resources.className = 'list-rows';
      recommendation.resources.forEach((resource) => {
        const row = document.createElement('div');
        row.className = 'list-row';
        const copy = document.createElement('div');
        copy.className = 'list-copy';
        const title = document.createElement('strong');
        title.textContent = resource.title;
        const detail = document.createElement('span');
        const typeLabels = {
          EDUCATIONAL_SEARCH: 'Educational search results',
          LEARNING_PAGE: 'Direct learning page',
          NOTES: 'Study notes',
          TUTORIAL: 'Tutorial',
          VIDEO_SEARCH: 'Video search results (not a specific verified video)',
        };
        detail.textContent = `${typeLabels[resource.type] || resource.type.replaceAll('_', ' ').toLowerCase()} · ${resource.topic}`;
        copy.append(title, detail);
        const link = document.createElement('a');
        link.className = 'button button-outline button-small';
        link.href = resource.url;
        link.target = '_blank';
        link.rel = 'noopener noreferrer';
        link.textContent = 'Open resource';
        link.setAttribute('aria-label', `Open ${resource.title} about ${resource.topic} in a new tab`);
        row.append(copy, link);
        resources.append(row);
      });
      section.append(resources);
      return section;
    };

    const renderRecommendations = (container, recommendations) => {
      if (recommendations.length === 0) {
        const empty = document.createElement('p');
        empty.className = 'muted';
        empty.textContent = 'No topics currently meet the review recommendation criteria. Keep practicing, or request resources for a topic you find difficult.';
        container.replaceChildren(empty);
        return;
      }
      container.replaceChildren(...recommendations.map(renderTopicRecommendation));
    };

    requestResourcePageData(
      '/api/student/resources/recommendations',
      'Unable to load learning recommendations.'
    )
      .then((recommendations) => {
        renderRecommendations(weakTopicList, recommendations);
      })
      .catch((error) => {
        const message = document.createElement('p');
        message.className = 'muted is-error';
        message.textContent = error.message;
        weakTopicList.replaceChildren(message);
      });

    requestResourcePageData('/api/student/quiz', 'Unable to load your saved study materials.')
      .then((overview) => {
        const materials = overview.materials || [];
        if (materials.length === 0) {
          const empty = document.createElement('p');
          empty.className = 'muted';
          empty.textContent = 'Your recent resources will appear here after you save study materials.';
          recentResourcesList.replaceChildren(empty);
          return;
        }

        const rows = materials.map((material) => {
          const row = document.createElement('div');
          row.className = 'list-row';

          const icon = document.createElement('span');
          icon.className = 'list-icon';
          icon.textContent = 'NOTE';

          const copy = document.createElement('div');
          copy.className = 'list-copy';
          const title = document.createElement('strong');
          title.textContent = material.topic;
          const detail = document.createElement('span');
          const savedDate = new Intl.DateTimeFormat(undefined, {
            dateStyle: 'medium',
            timeStyle: 'short',
          }).format(new Date(material.createdAt));
          detail.textContent = `Saved study material · ${savedDate}`;
          copy.append(title, detail);

          const label = document.createElement('span');
          label.className = 'pill';
          label.textContent = 'Study material';

          row.append(icon, copy, label);
          return row;
        });
        recentResourcesList.replaceChildren(...rows);
      })
      .catch((error) => {
        const message = document.createElement('p');
        message.className = 'muted is-error';
        message.textContent = error.message;
        recentResourcesList.replaceChildren(message);
      });

    difficultTopicForm.addEventListener('submit', async (event) => {
      event.preventDefault();
      const submitButton = difficultTopicForm.querySelector('button[type="submit"]');
      submitButton.disabled = true;
      difficultTopicMessage.textContent = '';
      difficultTopicMessage.classList.remove('is-error');
      try {
        const csrf = await fetchCsrfToken();
        const recommendation = await requestResourcePageData(
          '/api/student/resources/recommendations',
          'Unable to load learning recommendations.',
          {
            method: 'POST',
            headers: {
              'Content-Type': 'application/json',
              [csrf.headerName]: csrf.token,
            },
            body: JSON.stringify({ topic: difficultTopicInput.value }),
          },
        );
        renderRecommendations(reportedTopicList, [recommendation]);
      } catch (error) {
        difficultTopicMessage.textContent = error.message;
        difficultTopicMessage.classList.add('is-error');
      } finally {
        submitButton.disabled = false;
      }
    });

    resourcePdfForm.addEventListener('submit', async (event) => {
      event.preventDefault();
      const submitButton = resourcePdfForm.querySelector('button[type="submit"]');
      const file = resourcePdfForm.elements.namedItem('file').files[0];
      submitButton.disabled = true;
      resourcePdfMessage.textContent = 'Checking the PDF and matching readable text in memory...';
      resourcePdfMessage.classList.remove('is-error');
      resourcePdfRecommendations.replaceChildren();
      try {
        const csrf = await fetchCsrfToken();
        const formData = new FormData();
        formData.append('file', file);
        const result = await requestResourcePageData(
          '/api/student/resources/recommendations/pdf',
          'Unable to load learning recommendations.',
          {
            method: 'POST',
            headers: { [csrf.headerName]: csrf.token },
            body: formData,
          }
        );
        const megabytes = result.maxPdfBytes / (1024 * 1024);
        const sizeLabel = Number.isInteger(megabytes) ? `${megabytes} MB` : `${megabytes.toFixed(1)} MB`;
        resourcePdfLimits.textContent = `PDF limits: ${sizeLabel}, ${result.maxPdfPages} pages, and ${result.maxExtractedCharacters.toLocaleString()} extracted characters.`;
        resourcePdfMessage.textContent = result.summary;
        if (result.identifiedTopics.length === 0) {
          const manual = document.createElement('p');
          manual.className = 'muted';
          manual.textContent = 'Upload a clearer PDF with selectable text, or enter the topic in “what feels difficult” above.';
          resourcePdfRecommendations.replaceChildren(manual);
        } else {
          const topics = document.createElement('p');
          topics.className = 'muted';
          topics.textContent = `Likely topics found: ${result.identifiedTopics.join(', ')}.`;
          resourcePdfRecommendations.replaceChildren(
            topics,
            ...result.recommendations.map(renderTopicRecommendation)
          );
        }
      } catch (error) {
        resourcePdfMessage.textContent = error.message;
        resourcePdfMessage.classList.add('is-error');
      } finally {
        submitButton.disabled = false;
      }
    });
  }

  const quizPage = document.querySelector('[data-quiz-page]');
  if (quizPage) {
    const materialForm = quizPage.querySelector('[data-quiz-material-form]');
    const materialMessage = quizPage.querySelector('[data-quiz-material-message]');
    const pdfForm = quizPage.querySelector('[data-quiz-pdf-form]');
    const pdfMessage = quizPage.querySelector('[data-quiz-pdf-message]');
    const materialList = quizPage.querySelector('[data-quiz-materials]');
    const deleteMessage = quizPage.querySelector('[data-quiz-delete-message]');
    const progressList = quizPage.querySelector('[data-quiz-progress]');
    const masteryLabel = quizPage.querySelector('[data-quiz-mastery]');
    const topicInputs = quizPage.querySelectorAll('[data-quiz-topic-input]');
    const topicLimitNotes = quizPage.querySelectorAll('[data-quiz-topic-limit]');
    const pdfLimitNote = quizPage.querySelector('[data-quiz-pdf-limit]');
    const quizHeading = quizPage.querySelector('[data-quiz-heading]');
    const questionCount = quizPage.querySelector('[data-quiz-question-count]');
    const prompt = quizPage.querySelector('[data-quiz-prompt]');
    const answerForm = quizPage.querySelector('[data-quiz-answer-form]');
    const optionList = quizPage.querySelector('[data-quiz-options]');
    const feedback = quizPage.querySelector('[data-quiz-feedback]');
    const scoreLabel = quizPage.querySelector('[data-quiz-score]');
    const resultPanel = quizPage.querySelector('[data-quiz-result-panel]');
    const resultScore = quizPage.querySelector('[data-quiz-result-score]');
    const resultDetail = quizPage.querySelector('[data-quiz-result-detail]');
    const progressBar = quizPage.querySelector('[data-quiz-progress-bar]');
    const startButton = quizPage.querySelector('[data-quiz-start]');
    const nextButton = quizPage.querySelector('[data-quiz-next]');
    const resultButton = quizPage.querySelector('[data-quiz-result]');
    let activeAttempt = null;
    let activeQuestionIndex = 0;
    let finalResult = null;
    let selectedMaterialId = null;

    const displayMessage = (element, text, isError = false) => {
      element.textContent = text;
      element.classList.toggle('is-error', isError);
    };

    const apiRequest = async (url, options = {}) => {
      const response = await fetch(`${apiBase}${url}`, {
        credentials: 'include',
        ...options,
      });
      const result = response.status === 204 ? null : await response.json().catch(() => null);
      if (!response.ok) {
        throw new Error(result?.detail || result?.message || 'The quiz request could not be completed.');
      }
      return result;
    };

    const renderConceptProgress = (overview) => {
      const concepts = overview.concepts.filter((concept) => concept.materialId === selectedMaterialId);
      if (selectedMaterialId === null) {
        const empty = document.createElement('p');
        empty.className = 'muted';
        empty.textContent = 'Select a study material above to see its concept progress.';
        progressList.replaceChildren(empty);
        return;
      }
      if (concepts.length === 0) {
        const empty = document.createElement('p');
        empty.className = 'muted';
        empty.textContent = 'This material has no quiz concepts yet.';
        progressList.replaceChildren(empty);
        return;
      }

      const countStatus = (status) => concepts.filter((concept) => concept.status === status).length;
      const counts = document.createElement('p');
      counts.className = 'muted';
      counts.textContent = `${countStatus('NOT_YET_TESTED')} not yet tested · ${countStatus('IN_PROGRESS')} in progress · ${countStatus('NEEDS_REVISION')} needs revision · ${countStatus('MASTERED')} mastered`;
      const rows = concepts.map((concept) => {
        const row = document.createElement('div');
        row.className = 'list-row';
        const copy = document.createElement('div');
        copy.className = 'list-copy';
        const topic = document.createElement('strong');
        topic.textContent = concept.topic;
        const state = document.createElement('span');
        const statusLabels = {
          NOT_YET_TESTED: 'Not yet tested',
          IN_PROGRESS: `In progress · ${concept.consecutiveCorrect}/${concept.masteryTarget} correct in a row`,
          NEEDS_REVISION: 'Needs revision',
          MASTERED: 'Mastered',
        };
        state.textContent = statusLabels[concept.status] || concept.status;
        copy.append(topic, state);
        row.append(copy);
        return row;
      });
      progressList.replaceChildren(counts, ...rows);
    };

    const renderOverview = (overview) => {
      masteryLabel.textContent = `Mastery: ${overview.masteryTarget} consecutive correct answers per concept`;
      topicInputs.forEach((input) => {
        input.maxLength = overview.maxTopicCharacters;
      });
      topicLimitNotes.forEach((note) => {
        note.textContent = `Topics may be up to ${overview.maxTopicCharacters} characters.`;
      });
      const pdfMegabytes = overview.maxPdfBytes / (1024 * 1024);
      const pdfSizeLabel = Number.isInteger(pdfMegabytes) ? `${pdfMegabytes} MB` : `${pdfMegabytes.toFixed(1)} MB`;
      pdfLimitNote.textContent = `PDFs up to ${pdfSizeLabel}, ${overview.maxPdfPages} pages, and ${overview.maxExtractedCharacters.toLocaleString()} extracted characters. Scanned/image-only PDFs are not supported. Files are processed in memory and are not stored.`;
      if (!overview.materials.some((material) => material.id === selectedMaterialId)) {
        selectedMaterialId = null;
      }
      if (overview.materials.length === 0) {
        const empty = document.createElement('p');
        empty.className = 'muted';
        empty.textContent = 'No saved materials yet. Add studied material to create source-grounded questions.';
        materialList.replaceChildren(empty);
        startButton.disabled = true;
        prompt.textContent = 'No suitable study material is available yet. Add material you have studied first.';
        renderConceptProgress(overview);
        return;
      }

      const materialRows = overview.materials.map((material) => {
        const row = document.createElement('div');
        row.className = 'list-row';
        const copy = document.createElement('div');
        copy.className = 'list-copy';
        const choice = document.createElement('input');
        choice.type = 'radio';
        choice.name = 'quiz-material-choice';
        choice.value = material.id;
        choice.checked = material.id === selectedMaterialId;
        choice.setAttribute('aria-label', `Select ${material.topic} for quiz`);
        choice.addEventListener('change', () => {
          selectedMaterialId = material.id;
          startButton.disabled = false;
          prompt.textContent = `Questions will come only from ${material.topic}.`;
          displayMessage(deleteMessage, '');
          renderConceptProgress(overview);
        });
        const topic = document.createElement('strong');
        topic.textContent = material.topic;
        const detail = document.createElement('span');
        detail.textContent = `${material.conceptCount} concept${material.conceptCount === 1 ? '' : 's'}`;
        copy.append(choice, topic, detail);
        const removeButton = document.createElement('button');
        removeButton.type = 'button';
        removeButton.className = 'button button-light';
        removeButton.textContent = 'Remove';
        removeButton.setAttribute('aria-label', `Remove ${material.topic}`);
        removeButton.addEventListener('click', async () => {
          if (!window.confirm(`Remove “${material.topic}”? Historical quiz results will be retained.`)) {
            return;
          }
          removeButton.disabled = true;
          displayMessage(deleteMessage, '');
          try {
            const csrf = await fetchCsrfToken();
            await apiRequest(`/api/student/quiz/materials/${material.id}`, {
              method: 'DELETE',
              headers: { [csrf.headerName]: csrf.token },
            });
            row.remove();
            if (selectedMaterialId === material.id) {
              selectedMaterialId = null;
              startButton.disabled = true;
              renderConceptProgress({ ...overview, concepts: overview.concepts.filter(
                (concept) => concept.materialId !== material.id
              ) });
              prompt.textContent = 'Select another study material before starting a quiz.';
            }
            displayMessage(deleteMessage, 'Study material removed. Historical quiz results are retained.');
            try {
              await loadOverview();
            } catch (error) {
              displayMessage(deleteMessage, `Material removed, but the list could not be refreshed: ${error.message}`, true);
            }
          } catch (error) {
            removeButton.disabled = false;
            displayMessage(deleteMessage, error.message, true);
          }
        });
        row.append(copy, removeButton);
        return row;
      });
      materialList.replaceChildren(...materialRows);
      startButton.disabled = selectedMaterialId === null;
      renderConceptProgress(overview);
    };

    const loadOverview = async () => {
      const overview = await apiRequest('/api/student/quiz');
      renderOverview(overview);
    };

    const currentQuestion = () => activeAttempt.questions[activeQuestionIndex];
    const showCurrentQuestion = () => {
      const question = currentQuestion();
      if (!question) {
        displayMessage(feedback, 'The quiz has no further questions.', true);
        return;
      }
      quizHeading.textContent = question.topic;
      questionCount.textContent = `Question ${question.position} of ${question.totalQuestions}`;
      prompt.textContent = question.prompt;
      answerForm.hidden = false;
      const optionInputs = question.options.map((option) => {
        const label = document.createElement('label');
        label.className = 'quiz-option';
        const input = document.createElement('input');
        input.type = 'radio';
        input.name = 'answer';
        input.value = option.label;
        input.required = true;
        input.setAttribute('aria-label', `Option ${option.label}: ${option.text}`);
        const optionLabel = document.createElement('span');
        optionLabel.className = 'quiz-option-label';
        optionLabel.textContent = option.label;
        const optionText = document.createElement('span');
        optionText.textContent = option.text;
        label.append(input, optionLabel, optionText);
        return label;
      });
      optionList.replaceChildren(...optionInputs);
      answerForm.querySelector('button[type="submit"]').disabled = false;
      optionInputs[0]?.querySelector('input').focus();
      nextButton.hidden = true;
      resultButton.hidden = true;
      startButton.hidden = true;
      feedback.textContent = '';
      scoreLabel.textContent = `Score: ${activeAttempt.correctAnswers} of ${activeAttempt.totalQuestions}`;
      progressBar.style.width = `${((activeQuestionIndex) / activeAttempt.totalQuestions) * 100}%`;
    };

    loadOverview().catch((error) => {
      const empty = document.createElement('p');
      empty.className = 'muted is-error';
      empty.textContent = error.message;
      progressList.replaceChildren(empty);
      displayMessage(feedback, error.message, true);
    });

    materialForm.addEventListener('submit', async (event) => {
      event.preventDefault();
      const submitButton = materialForm.querySelector('button[type="submit"]');
      submitButton.disabled = true;
      submitButton.setAttribute('aria-busy', 'true');
      displayMessage(materialMessage, '');
      try {
        const csrf = await fetchCsrfToken();
        await apiRequest('/api/student/quiz/materials', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            [csrf.headerName]: csrf.token,
          },
          body: JSON.stringify({
            topic: materialForm.elements.namedItem('topic').value,
            sourceText: materialForm.elements.namedItem('sourceText').value,
          }),
        });
        materialForm.reset();
        displayMessage(materialMessage, 'Study material saved. Quiz questions will use its source statements.');
        await loadOverview();
      } catch (error) {
        displayMessage(materialMessage, error.message, true);
      } finally {
        submitButton.disabled = false;
        submitButton.removeAttribute('aria-busy');
      }
    });

    pdfForm.addEventListener('submit', async (event) => {
      event.preventDefault();
      const submitButton = pdfForm.querySelector('button[type="submit"]');
      submitButton.disabled = true;
      submitButton.setAttribute('aria-busy', 'true');
      displayMessage(pdfMessage, '');
      try {
        const csrf = await fetchCsrfToken();
        const formData = new FormData();
        formData.append('topic', pdfForm.elements.namedItem('topic').value);
        formData.append('file', pdfForm.elements.namedItem('file').files[0]);
        await apiRequest('/api/student/quiz/materials/pdf', {
          method: 'POST',
          headers: { [csrf.headerName]: csrf.token },
          body: formData,
        });
        pdfForm.reset();
        displayMessage(pdfMessage, 'PDF text extracted and saved as studied material.');
        try {
          await loadOverview();
        } catch (error) {
          displayMessage(pdfMessage, `PDF saved, but progress could not be refreshed: ${error.message}`, true);
        }
      } catch (error) {
        displayMessage(pdfMessage, error.message, true);
      } finally {
        submitButton.disabled = false;
        submitButton.removeAttribute('aria-busy');
      }
    });

    startButton.addEventListener('click', async () => {
      startButton.disabled = true;
      displayMessage(feedback, '');
      try {
        const csrf = await fetchCsrfToken();
        if (selectedMaterialId === null) {
          throw new Error('Select a study material before starting a quiz.');
        }
        activeAttempt = await apiRequest(`/api/student/quiz/materials/${selectedMaterialId}/attempts`, {
          method: 'POST',
          headers: { [csrf.headerName]: csrf.token },
        });
        activeQuestionIndex = 0;
        finalResult = null;
        resultPanel.hidden = true;
        resultScore.textContent = '';
        resultDetail.textContent = '';
        questionCount.textContent = `Question 1 of ${activeAttempt.totalQuestions}`;
        showCurrentQuestion();
      } catch (error) {
        displayMessage(feedback, error.message, true);
      } finally {
        startButton.disabled = false;
      }
    });

    answerForm.addEventListener('submit', async (event) => {
      event.preventDefault();
      const submitButton = answerForm.querySelector('button[type="submit"]');
      submitButton.disabled = true;
      const selectedOption = optionList.querySelector('input[name="answer"]:checked');
      if (!selectedOption) {
        submitButton.disabled = false;
        displayMessage(feedback, 'Choose an option before submitting.', true);
        return;
      }
      optionList.querySelectorAll('input[name="answer"]').forEach((input) => {
        input.disabled = true;
      });
      try {
        const csrf = await fetchCsrfToken();
        const result = await apiRequest(`/api/student/quiz/attempts/${activeAttempt.attemptId}/answers`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            [csrf.headerName]: csrf.token,
          },
          body: JSON.stringify({
            conceptId: currentQuestion().conceptId,
            answer: selectedOption.value,
          }),
        });
        activeAttempt.correctAnswers = result.correctAnswers;
        const correctness = result.correct ? 'Correct.' : 'Not quite.';
        const answerFeedback = `${correctness} The correct answer is ${result.correctOption}: ${result.correctAnswer}. ${result.explanation} Concept status: ${result.conceptStatus.replaceAll('_', ' ').toLowerCase()}.`;
        displayMessage(
          feedback,
          answerFeedback
        );
        scoreLabel.textContent = `Score: ${result.correctAnswers} of ${result.totalQuestions}`;
        progressBar.style.width = `${((activeQuestionIndex + 1) / result.totalQuestions) * 100}%`;
        if (result.completed) {
          finalResult = result;
          quizHeading.textContent = 'Quiz complete';
          questionCount.textContent = `Question ${activeQuestionIndex + 1} of ${result.totalQuestions} · Complete`;
          prompt.textContent = 'You have answered every question in this attempt.';
          answerForm.hidden = true;
          nextButton.hidden = true;
          resultButton.hidden = false;
          displayMessage(
            feedback,
            `${answerFeedback} All questions are answered. Select See Result to review your score.`
          );
          try {
            await loadOverview();
          } catch (error) {
            const refreshError = document.createElement('p');
            refreshError.className = 'muted is-error';
            refreshError.textContent = `Quiz result saved; progress could not be refreshed: ${error.message}`;
            progressList.prepend(refreshError);
          }
        } else {
          nextButton.hidden = false;
          nextButton.textContent = 'Next Question';
        }
      } catch (error) {
        optionList.querySelectorAll('input[name="answer"]').forEach((input) => {
          input.disabled = false;
        });
        submitButton.disabled = false;
        displayMessage(feedback, error.message, true);
      }
    });

    nextButton.addEventListener('click', () => {
      if (!activeAttempt || activeQuestionIndex + 1 >= activeAttempt.questions.length) {
        nextButton.hidden = true;
        displayMessage(feedback, 'There are no unanswered questions remaining.', true);
        return;
      }
      activeQuestionIndex += 1;
      showCurrentQuestion();
    });

    resultButton.addEventListener('click', () => {
      if (!finalResult?.completed) {
        resultButton.hidden = true;
        displayMessage(feedback, 'Finish the quiz before viewing the result.', true);
        return;
      }
      prompt.textContent = `Final result: ${finalResult.correctAnswers} of ${finalResult.totalQuestions} correct.`;
      questionCount.textContent = 'Final result';
      const percentage = Math.round((finalResult.correctAnswers / finalResult.totalQuestions) * 100);
      resultScore.textContent = `${finalResult.correctAnswers} / ${finalResult.totalQuestions}`;
      resultDetail.textContent = `${finalResult.correctAnswers} correct answers out of ${finalResult.totalQuestions} · ${percentage}%`;
      resultPanel.hidden = false;
      resultButton.hidden = true;
      startButton.hidden = false;
      startButton.textContent = 'Start another quiz';
      resultPanel.focus();
    });
  }

  const uploadPage = document.querySelector('[data-upload-page]');
  if (uploadPage) {
    const uploadForm = uploadPage.querySelector('[data-upload-form]');
    const uploadMessage = uploadPage.querySelector('[data-upload-message]');
    const uploadList = uploadPage.querySelector('[data-uploaded-notes]');
    const listMessage = uploadPage.querySelector('[data-upload-list-message]');
    const uploadLimits = uploadPage.querySelector('[data-upload-limits]');
    const fileNameLabel = uploadPage.querySelector('[data-file-name]');
    const submitButton = uploadForm.querySelector('button[type="submit"]');
    let maxPdfBytes = 20 * 1024 * 1024;

    const setUploadMessage = (element, text, isError = false) => {
      element.textContent = text;
      element.classList.toggle('is-error', isError);
    };

    const requestUploadApi = async (url, options = {}) => {
      const response = await fetch(`${apiBase}${url}`, {
        credentials: 'include',
        ...options,
      });
      const result = response.status === 204 ? null : await response.json().catch(() => null);
      if (!response.ok) {
        throw new Error(result?.detail || result?.message || 'The upload request could not be completed.');
      }
      return result;
    };

    const showEmptyUploadList = (message, isError = false) => {
      const empty = document.createElement('p');
      empty.className = isError ? 'muted is-error' : 'muted';
      empty.textContent = message;
      uploadList.replaceChildren(empty);
    };

    const renderUploadedNotes = (overview) => {
      const materials = overview.materials || [];
      if (materials.length === 0) {
        showEmptyUploadList('No study materials uploaded yet. Add a readable PDF to start.');
        return;
      }

      const rows = materials.map((material) => {
        const row = document.createElement('div');
        row.className = 'list-row';
        const icon = document.createElement('span');
        icon.className = 'list-icon';
        icon.textContent = material.originalFilename ? 'PDF' : 'NOTE';

        const copy = document.createElement('div');
        copy.className = 'list-copy';
        const title = document.createElement('strong');
        title.textContent = material.originalFilename || material.topic;
        const detail = document.createElement('span');
        detail.textContent = `${material.topic} · ${formatActivityDate(material.createdAt)} · ${material.conceptCount} concept${material.conceptCount === 1 ? '' : 's'}`;
        copy.append(title, detail);

        const removeButton = document.createElement('button');
        removeButton.type = 'button';
        removeButton.className = 'button button-light button-small';
        removeButton.textContent = 'Remove';
        removeButton.setAttribute('aria-label', `Remove ${material.originalFilename || material.topic}`);
        removeButton.addEventListener('click', async () => {
          if (!window.confirm(`Remove “${material.originalFilename || material.topic}” from your saved study materials?`)) return;
          removeButton.disabled = true;
          setUploadMessage(listMessage, '');
          try {
            const csrf = await fetchCsrfToken();
            await requestUploadApi(`/api/student/quiz/materials/${encodeURIComponent(material.id)}`, {
              method: 'DELETE',
              headers: { [csrf.headerName]: csrf.token },
            });
            setUploadMessage(listMessage, 'Study material removed.');
            try {
              await loadUploadedNotes();
            } catch (error) {
              setUploadMessage(listMessage, `Study material removed, but the list could not be refreshed: ${error.message}`, true);
            }
          } catch (error) {
            removeButton.disabled = false;
            setUploadMessage(listMessage, error.message, true);
          }
        });

        row.append(icon, copy, removeButton);
        return row;
      });
      uploadList.replaceChildren(...rows);
    };

    const loadUploadedNotes = async () => {
      const overview = await requestUploadApi('/api/student/quiz');
      if (Number.isFinite(overview.maxPdfBytes) && overview.maxPdfBytes > 0) {
        maxPdfBytes = overview.maxPdfBytes;
      }
      const maxMegabytes = maxPdfBytes / (1024 * 1024);
      const sizeLabel = Number.isInteger(maxMegabytes) ? `${maxMegabytes} MB` : `${maxMegabytes.toFixed(1)} MB`;
      uploadLimits.textContent = `PDFs up to ${sizeLabel}, ${overview.maxPdfPages} pages, and ${overview.maxExtractedCharacters.toLocaleString()} extracted characters. Scanned/image-only PDFs are not supported.`;
      renderUploadedNotes(overview);
    };

    uploadForm.addEventListener('submit', async (event) => {
      event.preventDefault();
      const file = uploadForm.elements.namedItem('file').files[0];
      const topic = uploadForm.elements.namedItem('topic').value.trim();
      if (!file) {
        setUploadMessage(uploadMessage, 'Choose a PDF file to upload.', true);
        return;
      }
      if (!file.name.toLowerCase().endsWith('.pdf') || (file.type && file.type !== 'application/pdf')) {
        setUploadMessage(uploadMessage, 'Choose a PDF file. Word, PowerPoint, and plain-text files are not supported.', true);
        return;
      }
      if (file.size > maxPdfBytes) {
        const maxMegabytes = maxPdfBytes / (1024 * 1024);
        const sizeLabel = Number.isInteger(maxMegabytes) ? `${maxMegabytes} MB` : `${maxMegabytes.toFixed(1)} MB`;
        setUploadMessage(uploadMessage, `PDF files must be no larger than ${sizeLabel}.`, true);
        return;
      }
      if (!topic || topic.length > 200) {
        setUploadMessage(uploadMessage, 'Enter a subject or topic of no more than 200 characters.', true);
        return;
      }

      submitButton.disabled = true;
      submitButton.setAttribute('aria-busy', 'true');
      setUploadMessage(uploadMessage, 'Uploading and extracting readable PDF text…');
      try {
        const csrf = await fetchCsrfToken();
        const formData = new FormData();
        formData.append('topic', topic);
        formData.append('file', file);
        const savedMaterial = await requestUploadApi('/api/student/quiz/materials/pdf', {
          method: 'POST',
          headers: { [csrf.headerName]: csrf.token },
          body: formData,
        });
        uploadForm.reset();
        fileNameLabel.textContent = 'No file selected';
        setUploadMessage(uploadMessage, `“${savedMaterial.originalFilename || savedMaterial.topic}” was uploaded. Extracted text is saved for Quiz; the original PDF is not retained.`);
        try {
          await loadUploadedNotes();
          setUploadMessage(listMessage, '');
        } catch (error) {
          setUploadMessage(listMessage, `Upload succeeded, but the saved-notes list could not be refreshed: ${error.message}`, true);
        }
      } catch (error) {
        setUploadMessage(uploadMessage, error.message, true);
      } finally {
        submitButton.disabled = false;
        submitButton.removeAttribute('aria-busy');
      }
    });

    loadUploadedNotes().catch((error) => {
      showEmptyUploadList(error.message, true);
      setUploadMessage(uploadMessage, error.message, true);
    });
  }

  document.querySelectorAll('[data-file-input]').forEach((input) => {
    input.addEventListener('change', () => {
      const fileName = input.files?.[0]?.name;
      const output = document.querySelector('[data-file-name]');
      if (output) output.textContent = fileName || 'No file selected';
    });
  });
})();