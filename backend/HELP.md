# Getting Started

### Reference Documentation
For further reference, please consider the following sections:

* [Official Apache Maven documentation](https://maven.apache.org/guides/index.html)
* [Spring Boot Maven Plugin Reference Guide](https://docs.spring.io/spring-boot/4.1.1.RELEASE/maven-plugin)
* [Create an OCI image](https://docs.spring.io/spring-boot/4.1.1.RELEASE/maven-plugin/build-image.html)
* [Spring Web](https://docs.spring.io/spring-boot/4.1.1.RELEASE/reference/web/servlet.html)
* [Spring Data JPA](https://docs.spring.io/spring-boot/4.1.1.RELEASE/reference/data/sql.html#data.sql.jpa-and-spring-data)
* [Validation](https://docs.spring.io/spring-boot/4.1.1.RELEASE/reference/io/validation.html)

### Guides
The following guides illustrate how to use some features concretely:

* [Building a RESTful Web Service](https://spring.io/guides/gs/rest-service/)
* [Serving Web Content with Spring MVC](https://spring.io/guides/gs/serving-web-content/)
* [Building REST services with Spring](https://spring.io/guides/tutorials/rest/)
* [Accessing Data with JPA](https://spring.io/guides/gs/accessing-data-jpa/)
* [Accessing data with MySQL](https://spring.io/guides/gs/accessing-data-mysql/)
* [Validation](https://spring.io/guides/gs/validating-form-input/)

### Local authentication

Open the application through Spring Boot at
`http://localhost:8080/pages/login.html` or
`http://localhost:8080/pages/register.html`. The frontend files are packaged
from `../frontend` and served by the backend. Authentication and learning pages
under `/pages/` require a server-side session; only login and registration
pages are public. Do not use a separate static file server for protected pages,
because it does not enforce Spring Security.

The frontend obtains a session-bound CSRF token from `GET /api/auth/csrf`
before sending registration, login, or logout requests. Successful
`POST /api/auth/login` requests create a server-side session and return `204`;
the browser then opens the protected dashboard. The session cookie is HttpOnly.
Invalid credentials return the same `401` message whether the email is unknown
or the password is incorrect. `POST /api/auth/logout` invalidates the session.

All API routes other than health, registration, login, CSRF bootstrap, and
logout require an authenticated student session. For HTTPS deployments, set
`SESSION_COOKIE_SECURE=true` so the session cookie is only sent over HTTPS.

### Maven Parent overrides

Due to Maven's design, elements are inherited from the parent POM to the project POM.
While most of the inheritance is fine, it also inherits unwanted elements like `<license>` and `<developers>` from the parent.
To prevent this, the project POM contains empty overrides for these elements.
If you manually switch to a different parent and actually want the inheritance, you need to remove those overrides.
