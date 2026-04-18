# picture-server

A Java application serving pictures from a directory with album-style browsing and password-protected access.

## Configuration

Create `settings.yaml` in the current working directory:

```yaml
path: ./pictures
port: 8080
password: secret
```

## Run

```bash
./gradlew run
```

## Test

```bash
./gradlew clean test
```
