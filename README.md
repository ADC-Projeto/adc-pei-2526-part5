# APDC-PEI 25/26 — First Web Application (Part 5)

Building on Part 4, this version replaces rudimentary auth tokens with **proper JWT-based session cookies**. Authentication is validated on protected endpoints using signed JWTs.

---

## What changed from Part 4

- Login now issues a **signed JWT** stored in an `HttpOnly` session cookie (`session::apdc`)
- A new **`JWTToken`** class handles JWT creation, validation, and decoding using the `auth0/java-jwt` library
- A new **`JWTConfig`** class centralises algorithm configuration — supports **HMAC** (HS256/384/512), **RSA** (RS256/384/512), and **ECDSA** (ES256/384/512), just choose the one wanted
- A new **`POST /rest/login/create`** endpoint allows registering users in memory
- The **`/rest/utils/time`** endpoint is **protected** — requires a valid JWT with role `Admin`
- Users are stored in an **in-memory `HashMap`** (no database yet)

---

## What this app does

The app serves a simple web page with the following available services:

- **`POST /rest/login/`** - authenticate and receive a JWT session cookie
- **`GET /rest/login/{username}`** - checks whether a username is already taken
- **`GET /rest/utils/hello`** - intentionally throws an exception and redirects to `/error/500.html`
- **`GET /rest/utils/time`** - returns the current server time in JSON (Admin Only)
- **`GET /rest/utils/compute`** - enqueues an async computation task via Cloud Tasks
- **`POST /rest/login/create`** -  register a new user

---

## Authentication & JWT Cookie

On a successful login, the server creates a **JWT** signed with the configured algorithm and sets it as an `HttpOnly` cookie named `session::apdc`. The JWT contains:

| Claim | Description |
|---|---|
| `sub` | Username |
| `iat` | Issued at (timestamp) |
| `exp` | Expiration time (2 hours) |
| `role` | User role (`Regular`, `Backoffice`, or `Admin`) |
| `email` | User email |
| `...` | Feel free to add more |

On protected endpoints, the server extracts and verifies the JWT from the cookie, then checks that the user's role meets the required level.

**Roles** (in ascending order of privilege):

| Role | Level |
|---|---|
| `Regular` | 0 |
| `Backoffice` | 1 |
| `Admin` | 2 |

> ⚠️ By default, users created via `/rest/login/create` get the `Regular` role. The username `admin` (case-insensitive) is automatically assigned the `Admin` role.

---

## JWT Configuration

Algorithm and secret are configured in `JWTConfig.java`. The default is **HS256** with a shared secret:

```java
public static final AlgorithmType ALGORITHM = AlgorithmType.HS256;
public static final String HMAC_SECRET = "change-me-to-a-secure-random-string";
public static final long EXPIRATION_TIME = 1000 * 60 * 60 * 2; // 2 hours
```

> ⚠️ Always change `HMAC_SECRET` to a strong, random value before deploying to production.

To switch to RSA or ECDSA, change the `ALGORITHM` field — key pairs are generated automatically at startup.

---

## Prerequisites

Before you begin, make sure you have the following installed:

- [Java 21](https://www.oracle.com/java/technologies/javase/jdk21-archive-downloads.html)
- [Apache Maven](https://maven.apache.org/install.html)
- [Git](https://git-scm.com/)
- [Google Cloud SDK](https://cloud.google.com/sdk/docs/install) (for cloud deployment)
- [Eclipse IDE](https://www.eclipse.org/downloads/) with the Maven plugin

---

## Getting Started

### 1. Fork and clone the repository

Fork the project on GitHub, then clone your fork locally:

```bash
git clone git@github.com:APDC-Projeto/apdc-pei-2526-part5.git
cd apdc-pei-2526-part5
```

### 2. Import into Eclipse

1. Open Eclipse and go to **File → Import → Maven → Existing Maven Projects**
2. Navigate to the folder where you cloned the project
3. Select it and click **Finish**
4. Eclipse will resolve dependencies automatically — check for any errors in the **Problems** tab

---

## Building the project

From the project root, run:

```bash
mvn clean package
```

If the build succeeds, you'll find the compiled `.war` file at:

```
target/Firstwebapp-0.0.1.war
```

---

## Running locally

Start the local App Engine dev server with:

```bash
mvn appengine:run
```

Then open your browser and go to:

```
http://localhost:8080/
```

## Testing with Postman

- Open [Postman](https://www.postman.com/downloads/) and create new requests:

1. Create a user:
    - Method: **POST**
    - URL: `http://localhost:8080/rest/login/create`
    - Body (raw JSON):
      ```json
      {
        "username": "admin",
        "password": "password"
      }
      ```

> Using `admin` as the username automatically assigns the `Admin` role, which is required to access `/rest/utils/time`.

2. Login
    - Method: **POST**
    - URL: `http://localhost:8080/rest/login/`
    - Body (raw JSON):
    ```json
    {
      "username": "admin",
      "password": "password"
    }
    ```

  > On success, the response body contains the JWT token and the `session::apdc` cookie is set automatically. Postman will store and send it on subsequent requests.

3. Access a protected endpoint
    - Method: **GET**
    - URL: `http://localhost:8080/rest/utils/time`

  > With a valid `Admin` JWT cookie this returns the current server time. Without it, returns `403 Forbidden`.

4. Check username availability
    - Method: **GET**
    - URL: `http://localhost:8080/rest/login/admin`

  > Returns `false` if the username is taken, `true` if it is available.

---

## Deploying to Google App Engine

### 1. Create a project on Google Cloud Console

Go to https://console.cloud.google.com/ and create a new project. Take note of the Project ID.

### 2. Update the project ID in `ComputationResource.java`

Before deploying, update the `projectId` field in the `/compute` endpoint:

```java
String projectId = "YOUR_PROJECT_ID"; // replace with your actual project ID
```

### 3. Authenticate with Google Cloud

```bash
gcloud auth login
gcloud config set project <your-proj-id>
```

### 4. Deploy

```bash
mvn appengine:deploy -Dapp.deploy.projectId=<your-proj-id> -Dapp.deploy.version=<version-number>
```

After a successful deploy, your app will be live at:

```
https://<your-project-id>.appspot.com/
```

---

## Project Structure

```
src/
└── main/
    ├── java/
    │   └── pt/unl/fct/di/apdc/firstwebapp/
    │       ├── authentication/
    │       │   └── JWTToken.java                        ← JWT create, validate, decode
    │       ├── filters/
    │       │   └── AdditionalResponseHeadersFilter.java ← CORS filter
    │       ├── resources/
    │       │   ├── ComputationResource.java             ← Utility endpoints + Cloud Tasks trigger
    │       │   └── LoginResource.java                   ← Login, register, session management
    │       └── util/
    │           ├── JWTConfig.java                       ← Algorithm & secret configuration
    │           ├── LoginData.java                       ← Login request model
    │           └── UserData.java                        ← User model
    └── webapp/
        ├── index.html                                   ← Front page
        ├── secret/
        │   └── index.html                               ← Protected static content
        ├── error/
        │   ├── 404.html                                 ← Custom 404 page
        │   └── 500.html                                 ← Custom 500 page
        ├── img/
        │   ├── cat.png
        │   └── jedi.gif
        └── WEB-INF/
            ├── web.xml                                  ← Servlet config
            └── appengine-web.xml                        ← App Engine config
```

---

## License

See [LICENSE](LICENSE) for details.

---

*FCT NOVA — ADC-PEI 2025/2026*
