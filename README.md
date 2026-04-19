# picture-server

A Java application serving pictures from a directory with album-style browsing and password-protected access.

## Configuration

Create `settings.properties` in the current working directory:

```properties
path = ./pictures
port = 8088
username = admin
password = secret
```

## Run

```bash
./gradlew run
```

## Test

```bash
./gradlew clean test
```
