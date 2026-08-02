# Política de Versionado

Este proyecto usa **Versionado Semántico** para releases estables y builds de desarrollo (nightly).

## VersionName

| Tipo | Formato | Ejemplo | Uso |
|------|---------|---------|-----|
| **Stable** | `MAYOR.MENOR` | `2.5` | Release estable para usuarios. Se taggea `vX.Y` y se publica en GitHub Releases. |
| **Nightly** | `MAYOR.MENOR-nightly.<YYMMDD>` | `2.5-nightly.260208` | Build de desarrollo con los últimos cambios, para testing. No se taggea. |

- **MAYOR**: cambia con cambios grandes de arquitectura o features incompatibles.
- **MENOR**: cambia con cada release estable (nuevas features + fixes).
- La fecha `YYMMDD` de las nightly se define al crear el build.

## VersionCode

Android requiere que el `versionCode` siempre aumente para poder instalar una versión sobre otra (`adb install -r`).

| Tipo | Rango | Ejemplo |
|------|-------|---------|
| **Stable** | Enteros secuenciales desde `1` | `9`, `10`, `11` ... |
| **Nightly** | `1000000 + YYMMDD` | `1000000 + 260208 = 1260208` |

- El versionCode de una nightly siempre queda por encima del último stable, permitiendo instalar la nightly sobre la stable.
- Entre nightlies, la fecha (YYMMDD) garantiza que la más nueva tenga mayor versionCode.

## Flujo de trabajo

### Release STABLE
1. Bump `versionCode` al siguiente entero.
2. Set `versionName` a `X.Y` (sin sufijo).
3. Compilar `assembleDebug` y verificar en dispositivo.
4. Actualizar `README.md` (badge de versión + changelog).
5. Commit + tag `vX.Y` + push.
6. Crear GitHub Release `vX.Y` con el APK adjunto.

### Build NIGHTLY
1. Set `versionName = "X.Y-nightly.<YYMMDD>"`.
2. Set `versionCode = 1000000 + YYMMDD`.
3. Compilar e instalar en el dispositivo de pruebas.
4. No taggear ni publicar, salvo que se decida un release "Nightly" aparte.

## Historial

| VersionName | VersionCode | Tipo | Fecha |
|-------------|-------------|------|-------|
| 2.4 | 8 | Stable | 2026-08-02 |
| 2.5 | 9 | Stable | 2026-08-02 |
