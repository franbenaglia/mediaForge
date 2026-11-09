# Release

Cómo se generan los APK firmados que van a GitHub Releases (T12.2).

## La clave de firma

**Se crea una sola vez y es la que permite que cada versión nueva se instale como actualización.**

- Si se pierde, nadie que tenga la app instalada puede actualizar sin desinstalar antes, y pierde su historial.
- Se guarda fuera del repo, con al menos una copia fuera de esta máquina: por ejemplo, un pendrive cifrado o un gestor de contraseñas que guarde archivos.

Crearla (una vez):

```bash
mkdir -p ~/keys
keytool -genkeypair -v \
    -keystore ~/keys/mediaforge-release.jks \
    -alias mediaforge \
    -keyalg RSA -keysize 4096 -validity 10000
```

`keytool` pide:

- la contraseña del keystore;
- la de la clave (puede ser la misma);
- unos datos de identificación: alcanza con el nombre y el resto puede quedar vacío.

Esos datos aparecen en el certificado de cada APK, así que son públicos.

Después, crear `keystore.properties` en la raíz del repo. `.gitignore` lo ignora (igual que `*.jks` y `*.keystore`) y **nunca va a un commit**:

```properties
storeFile=/home/<usuario>/keys/mediaforge-release.jks
storePassword=...
keyAlias=mediaforge
keyPassword=...
```

## Generar

```bash
./scripts/build-release.sh
```

El script:

1. corre `verify.sh`;
2. compila con R8 (`assembleRelease`);
3. verifica la firma de cada APK con `apksigner`;
4. escribe `SHA256SUMS`.

Deja en `app/build/outputs/apk/release/`:

- `app-arm64-v8a-release.apk`: la mayoría de los teléfonos.
- `app-x86_64-release.apk`: Chromebooks y equipos x86.
- `SHA256SUMS`: las sumas para publicar junto a los APK.

Antes de publicar, instalar el APK arm64 limpio en el teléfono y hacer una descarga. El código con R8 es el mismo que se probó con la variante `minified`, pero la firma es otra.

## Verificar un APK publicado

```bash
apksigner verify --print-certs app-arm64-v8a-release.apk
sha256sum -c SHA256SUMS
```

La huella SHA-256 del certificado se publica en las notas del release y en la landing, para que cualquiera pueda comprobar que un APK es el original.

## Versiones

- `versionCode` sube de a 1 con cada APK publicado.
- `versionName` sigue SemVer (`1.0.0`).
- Los dos están en `app/build.gradle.kts`. El historial va en `CHANGELOG.md`.
