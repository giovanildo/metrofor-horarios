#!/usr/bin/env bash
#
# Instala o ambiente de build do app (JDK 21, Gradle 9.8 e Android SDK 37) e
# compila o APK de debug.
#
#   bash tools/setup-dev.sh
#
# Tudo vai para o seu diretorio de usuario -- nao usa sudo e nao toca no
# sistema. Para remover, basta apagar ~/.local/opt e ~/Android/Sdk.
#
# Se preferir o JDK da distro, rode antes:
#   sudo apt install openjdk-21-jdk
# O script detecta um JDK 21 ja instalado e pula essa etapa.

set -euo pipefail

JDK_MAJOR=21
GRADLE_VERSION=9.8.0
ANDROID_PLATFORM=37.0
ANDROID_BUILD_TOOLS=37.0.0
CMDLINE_TOOLS_ZIP=commandlinetools-linux-11076708_latest.zip

PREFIX="$HOME/.local/opt"
JDK_DIR="$PREFIX/jdk-$JDK_MAJOR"
GRADLE_DIR="$PREFIX/gradle-$GRADLE_VERSION"
ANDROID_HOME="$HOME/Android/Sdk"
ENV_FILE="$PREFIX/android-env.sh"
PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

step() { printf '\n\033[1;34m==> %s\033[0m\n' "$*"; }
info() { printf '    %s\n' "$*"; }
fail() { printf '\n\033[1;31mERRO: %s\033[0m\n' "$*" >&2; exit 1; }

for cmd in curl unzip tar python3 sha256sum; do
    command -v "$cmd" >/dev/null || fail "'$cmd' nao encontrado. Instale com: sudo apt install $cmd"
done

need_space_mb=4096
avail_mb=$(df -Pm "$HOME" | awk 'NR==2 {print $4}')
[ "$avail_mb" -ge "$need_space_mb" ] || fail "espaco insuficiente em $HOME: ${avail_mb}MB livres, precisa de ~${need_space_mb}MB"

mkdir -p "$PREFIX"

# ---------------------------------------------------------------- JDK 21 ----
detect_jdk() {
    # Um JDK 21 ja instalado (apt, sdkman, etc.) serve.
    for candidate in "$JDK_DIR" "${JAVA_HOME:-}" /usr/lib/jvm/java-${JDK_MAJOR}-openjdk-amd64 /usr/lib/jvm/temurin-${JDK_MAJOR}-jdk-amd64; do
        [ -n "$candidate" ] && [ -x "$candidate/bin/javac" ] || continue
        if "$candidate/bin/javac" -version 2>&1 | grep -q " $JDK_MAJOR\."; then
            echo "$candidate"
            return 0
        fi
    done
    return 1
}

step "JDK $JDK_MAJOR"
if found_jdk="$(detect_jdk)"; then
    JDK_DIR="$found_jdk"
    info "ja instalado em $JDK_DIR"
else
    info "consultando a API do Eclipse Adoptium..."
    api="https://api.adoptium.net/v3/assets/latest/$JDK_MAJOR/hotspot?architecture=x64&image_type=jdk&os=linux&vendor=eclipse"
    jdk_meta="$(curl -fsSL "$api" | python3 -c '
import json, sys
assets = json.load(sys.stdin)
if not assets:
    sys.exit("nenhum binario retornado pela API")
pkg = assets[0]["binary"]["package"]
print(pkg["link"], pkg["checksum"], pkg["name"])
')" || fail "nao foi possivel consultar a API do Adoptium"
    read -r jdk_url jdk_sha jdk_name <<<"$jdk_meta"
    [ -n "$jdk_url" ] && [ -n "$jdk_sha" ] || fail "resposta inesperada da API do Adoptium"

    info "baixando $jdk_name"
    curl -fL --retry 3 --progress-bar "$jdk_url" -o "$WORK/jdk.tar.gz"

    info "conferindo checksum"
    echo "$jdk_sha  $WORK/jdk.tar.gz" | sha256sum -c - >/dev/null || fail "checksum do JDK nao confere"

    info "extraindo para $JDK_DIR"
    rm -rf "$JDK_DIR"
    mkdir -p "$JDK_DIR"
    tar -xzf "$WORK/jdk.tar.gz" -C "$JDK_DIR" --strip-components=1
fi

export JAVA_HOME="$JDK_DIR"
export PATH="$JAVA_HOME/bin:$PATH"
info "$("$JAVA_HOME/bin/java" -version 2>&1 | head -1)"

# ---------------------------------------------------------------- Gradle ----
step "Gradle $GRADLE_VERSION"
if [ -x "$GRADLE_DIR/bin/gradle" ]; then
    info "ja instalado em $GRADLE_DIR"
else
    base="https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"
    info "baixando gradle-$GRADLE_VERSION-bin.zip"
    curl -fL --retry 3 --progress-bar "$base" -o "$WORK/gradle.zip"

    info "conferindo checksum"
    sha="$(curl -fsSL "$base.sha256")" || fail "nao foi possivel baixar o checksum do Gradle"
    echo "$sha  $WORK/gradle.zip" | sha256sum -c - >/dev/null || fail "checksum do Gradle nao confere"

    info "extraindo para $GRADLE_DIR"
    rm -rf "$GRADLE_DIR"
    unzip -q "$WORK/gradle.zip" -d "$WORK/gradle-out"
    mv "$WORK/gradle-out/gradle-$GRADLE_VERSION" "$GRADLE_DIR"
fi
export PATH="$GRADLE_DIR/bin:$PATH"
info "$(gradle --version | grep '^Gradle')"

# ---------------------------------------------------------- Android SDK ----
step "Android SDK"
sdkmanager_bin="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
if [ -x "$sdkmanager_bin" ]; then
    info "command-line tools ja instaladas"
else
    info "baixando $CMDLINE_TOOLS_ZIP"
    curl -fL --retry 3 --progress-bar \
        "https://dl.google.com/android/repository/$CMDLINE_TOOLS_ZIP" -o "$WORK/clt.zip"

    info "extraindo para $ANDROID_HOME/cmdline-tools/latest"
    # O zip do Google descompacta como "cmdline-tools/", mas o sdkmanager exige
    # estar em "cmdline-tools/latest/" para achar as proprias dependencias.
    unzip -q "$WORK/clt.zip" -d "$WORK/clt-out"
    mkdir -p "$ANDROID_HOME/cmdline-tools"
    rm -rf "$ANDROID_HOME/cmdline-tools/latest"
    mv "$WORK/clt-out/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
fi

export ANDROID_HOME
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/emulator:$ANDROID_HOME/platform-tools:$PATH"

step "Aceitando as licencas do SDK"
info "sao as licencas padrao do Android SDK, exigidas pelo sdkmanager"
yes | sdkmanager --sdk_root="$ANDROID_HOME" --licenses >/dev/null 2>&1 || true

step "Instalando os pacotes do SDK"
info "platform-tools, platforms;android-$ANDROID_PLATFORM, build-tools;$ANDROID_BUILD_TOOLS"
sdkmanager --sdk_root="$ANDROID_HOME" \
    "platform-tools" \
    "platforms;android-$ANDROID_PLATFORM" \
    "build-tools;$ANDROID_BUILD_TOOLS"

# ------------------------------------------------------------ ambiente -----
step "Gravando o ambiente em $ENV_FILE"
cat > "$ENV_FILE" <<ENV
# Ambiente de build Android -- gerado por tools/setup-dev.sh
export JAVA_HOME="$JDK_DIR"
export ANDROID_HOME="$ANDROID_HOME"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="\$JAVA_HOME/bin:$GRADLE_DIR/bin:\$ANDROID_HOME/cmdline-tools/latest/bin:\$ANDROID_HOME/emulator:\$ANDROID_HOME/platform-tools:\$PATH"
ENV

marker="# >>> android-env (metrofor-horarios) >>>"
if grep -qF "$marker" "$HOME/.bashrc" 2>/dev/null; then
    info "~/.bashrc ja carrega esse ambiente"
else
    printf '\n%s\n' "Adicionar o ambiente ao seu ~/.bashrc? [s/N]"
    read -r answer </dev/tty || answer=n
    if [ "${answer,,}" = "s" ]; then
        {
            printf '\n%s\n' "$marker"
            printf '%s\n' "[ -f \"$ENV_FILE\" ] && . \"$ENV_FILE\""
            printf '%s\n' "# <<< android-env (metrofor-horarios) <<<"
        } >> "$HOME/.bashrc"
        info "adicionado ao ~/.bashrc"
    else
        info "nao adicionado -- carregue manualmente com: . $ENV_FILE"
    fi
fi

# -------------------------------------------------------------- build -----
step "Preparando o projeto"
cd "$PROJECT_DIR"
printf 'sdk.dir=%s\n' "$ANDROID_HOME" > local.properties
info "local.properties escrito"

if [ ! -f gradlew ]; then
    info "gerando o gradle wrapper"
    gradle wrapper --gradle-version "$GRADLE_VERSION" --quiet
fi

step "Compilando o APK de debug"
./gradlew assembleDebug

apk="$PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk"
if [ -f "$apk" ]; then
    printf '\n\033[1;32m==> Pronto\033[0m\n'
    info "APK: $apk ($(du -h "$apk" | cut -f1))"
    info ""
    info "Para instalar num aparelho com depuracao USB ativada:"
    info "  adb install -r \"$apk\""
    info ""
    info "Em um terminal novo, carregue o ambiente antes de usar gradle/adb:"
    info "  . $ENV_FILE"
else
    fail "o build terminou mas o APK nao foi encontrado em $apk"
fi
