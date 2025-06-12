# release.yml
name: Build & Release APK

on:
  push:
    tags:
      - 'v*'

env:
  # Using a modern JDK like 17 is recommended for running Gradle itself.
  # This is compatible with your project's target of Java 11.
  JAVA_VERSION: '17'
  GRADLE_OPTS: -Dorg.gradle.daemon=false -Dorg.gradle.parallel=true -Dorg.gradle.caching=true -Dorg.gradle.configureondemand=true -Dkotlin.incremental=false -Dkotlin.compiler.execution.strategy=in-process

jobs:
  build-and-release:
    name: Build & Release APK
    runs-on: ubuntu-latest
    timeout-minutes: 45
    permissions:
      contents: write

    steps:
      - name: 🔍 Checkout Repository
        uses: actions/checkout@v4
        with:
          fetch-depth: 0

      - name: 📊 Setup Build Environment Info
        id: build-info
        run: |
          echo "::notice title=Build Info::Building version ${{ github.ref_name }} from commit ${{ github.sha }}"
          echo "build-time=$(date -u +'%Y-%m-%dT%H:%M:%SZ')" >> $GITHUB_OUTPUT
          echo "version=${{ github.ref_name }}" >> $GITHUB_OUTPUT

      - name: ☕ Setup Java (JDK)
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: ${{ env.JAVA_VERSION }}
          cache: 'gradle'

      - name: 🤖 Setup Android SDK
        uses: android-actions/setup-android@v3

      # Granting execute permission for gradlew is a good practice in CI
      - name: 🔐 Grant Gradle Execution Permissions
        run: chmod +x ./gradlew

      - name: 🔨 Build Universal Release & Debug APKs
        run: |
          echo "::group::Building Universal APK files with bundle size optimization"
          ./gradlew assembleRelease assembleDebug \
            --stacktrace \
            --no-daemon \
            --parallel \
            --configure-on-demand \
            -Pandroid.enableR8.fullMode=true \
            -Pandroid.enableR8=true \
            -Pandroid.useAndroidX=true \
            -Pandroid.enableJetifier=true \
            -Pandroid.bundle.enableUncompressedNativeLibs=false \
            -Pandroid.injected.build.api=30 \
            -Pandroid.injected.build.abi=universal \
            -Dorg.gradle.jvmargs="-Xmx4g -XX:+UseG1GC -XX:+UseStringDeduplication" \
            -Dkotlin.incremental=false \
            -Dkotlin.compiler.execution.strategy=in-process
          echo "::endgroup::"

      - name: 🔎 Verify Universal APK Generation
        id: verify-apks
        run: |
          RELEASE_APKS_PATH="app/build/outputs/apk/release"
          DEBUG_APKS_PATH="app/build/outputs/apk/debug"
          
          echo "::group::Verifying Generated Universal APK files"
          if [ -d "$RELEASE_APKS_PATH" ]; then
            echo "Release APKs found:"
            ls -la "$RELEASE_APKS_PATH"/*.apk
            echo "release_path=$RELEASE_APKS_PATH" >> $GITHUB_OUTPUT
            
            # Get file sizes for release APKs
            for apk in "$RELEASE_APKS_PATH"/*.apk; do
              if [ -f "$apk" ]; then
                SIZE=$(du -h "$apk" | cut -f1)
                echo "::notice::Release APK: $(basename "$apk") - Size: $SIZE"
              fi
            done
          else
            echo "::warning::Release APK directory not found."
            echo "release_path=" >> $GITHUB_OUTPUT
          fi
          
          if [ -d "$DEBUG_APKS_PATH" ]; then
            echo "Debug APKs found:"
            ls -la "$DEBUG_APKS_PATH"/*.apk
            echo "debug_path=$DEBUG_APKS_PATH" >> $GITHUB_OUTPUT
            
            # Get file sizes for debug APKs
            for apk in "$DEBUG_APKS_PATH"/*.apk; do
              if [ -f "$apk" ]; then
                SIZE=$(du -h "$apk" | cut -f1)
                echo "::notice::Debug APK: $(basename "$apk") - Size: $SIZE"
              fi
            done
          else
            echo "::warning::Debug APK directory not found."
            echo "debug_path=" >> $GITHUB_OUTPUT
          fi

          APK_COUNT=$(find app/build/outputs/apk -name "*.apk" 2>/dev/null | wc -l)
          if [ "$APK_COUNT" -eq 0 ]; then
            echo "::error title=Build Failed::No APK files were generated."
            exit 1
          fi
          
          echo "::notice title=Build Success::$APK_COUNT Universal APK files generated successfully."
          echo "::endgroup::"

      - name: 📤 Upload APKs to GitHub Release
        uses: softprops/action-gh-release@v2
        with:
          files: |
            ${{ steps.verify-apks.outputs.release_path }}/*.apk
            ${{ steps.verify-apks.outputs.debug_path }}/*.apk
          fail_on_unmatched_files: false
          generate_release_notes: true
          body: |
            ## 📱 Universal APK Release
            
            This release contains universal APKs that work on all Android architectures.
            
            **Build Information:**
            - Version: ${{ steps.build-info.outputs.version }}
            - Built: ${{ steps.build-info.outputs.build-time }}
            - Commit: ${{ github.sha }}
            
            **APK Types:**
            - **Release APKs**: Optimized production builds
            - **Debug APKs**: Development builds with debugging symbols
            
            **Download the appropriate APK for your needs:**
            - For production use: Download the **release** APK
            - For testing/development: Download the **debug** APK
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}

      - name: 📱 Send Universal APKs to Telegram
        if: success() && secrets.TELEGRAM_BOT_TOKEN != '' && secrets.TELEGRAM_CHAT_ID != ''
        env:
          TELEGRAM_BOT_TOKEN: ${{ secrets.TELEGRAM_BOT_TOKEN }}
          TELEGRAM_CHAT_ID: ${{ secrets.TELEGRAM_CHAT_ID }}
        run: |
          echo "::group::Sending Universal APKs to Telegram"
          
          # Check if secrets are configured
          if [[ -z "$TELEGRAM_BOT_TOKEN" || -z "$TELEGRAM_CHAT_ID" ]]; then
            echo "::warning::Telegram bot token or chat ID not configured. Skipping Telegram notification."
            echo "::endgroup::"
            exit 0
          fi
          
          send_apk() {
            local FILE_PATH=$1
            local APK_TYPE=$2
            if [[ -z "$FILE_PATH" || ! -f "$FILE_PATH" ]]; then
              echo "::error::Invalid file path provided to send_apk function."
              return 1
            fi

            local FILE_NAME=$(basename "$FILE_PATH")
            local FILE_SIZE=$(du -h "$FILE_PATH" | cut -f1)
            local FILE_SIZE_BYTES=$(stat -c%s "$FILE_PATH")
            local FILE_SIZE_MB=$((FILE_SIZE_BYTES / 1024 / 1024))
            local NL=$'\n'
            
            # Create different icons and descriptions for release vs debug
            local ICON="📦"
            local TYPE_DESC="Universal"
            if [[ "$FILE_NAME" == *"debug"* ]]; then
              ICON="🔧"
              TYPE_DESC="Debug Universal"
            elif [[ "$FILE_NAME" == *"release"* ]]; then
              ICON="🚀"
              TYPE_DESC="Release Universal"
            fi
            
            local CAPTION="${ICON} *${FILE_NAME}*${NL}📱 Type: *${TYPE_DESC} APK*${NL}🔖 Version: *${{ steps.build-info.outputs.version }}*${NL}📅 Built: *${{ steps.build-info.outputs.build-time }}*${NL}📏 Size: *${FILE_SIZE}* (${FILE_SIZE_MB} MB)${NL}🏗️ Architecture: *Universal (all devices)*${NL}✨ Optimized with R8 & ProGuard"

            echo "::notice::Sending ${FILE_NAME} (${FILE_SIZE}) to Telegram..."
            
            # Test the bot token first
            TEST_RESPONSE=$(curl -s "https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/getMe")
            if ! echo "$TEST_RESPONSE" | grep -q '"ok":true'; then
              echo "::error title=Telegram Config Error::Invalid bot token. Response: $TEST_RESPONSE"
              return 1
            fi
            
            # Check file size limit (50MB for Telegram Bot API)
            if [ "$FILE_SIZE_BYTES" -gt 52428800 ]; then
              echo "::warning title=File Too Large::${FILE_NAME} is ${FILE_SIZE} (${FILE_SIZE_MB} MB), exceeding Telegram's 50MB limit. Skipping..."
              return 0
            fi
            
            RESPONSE=$(curl -s -w "\n%{http_code}" -X POST \
              "https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/sendDocument" \
              -F chat_id="${TELEGRAM_CHAT_ID}" \
              -F document=@"$FILE_PATH" \
              -F caption="$CAPTION" \
              -F parse_mode=Markdown \
              --connect-timeout 30 \
              --max-time 300)
            
            HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
            BODY=$(echo "$RESPONSE" | head -n -1)

            if [ "$HTTP_CODE" -eq 200 ]; then
              echo "::notice title=Telegram Success::✅ Successfully sent ${FILE_NAME}"
            else
              echo "::error title=Telegram Failed::❌ Failed to send ${FILE_NAME} (HTTP $HTTP_CODE)"
              echo "Response body: $BODY"
              
              # Provide helpful error messages
              if [ "$HTTP_CODE" -eq 404 ]; then
                echo "::error::HTTP 404 usually means invalid bot token or the bot can't access the chat."
                echo "::error::Make sure your bot token is correct and the bot has been added to the chat."
              elif [ "$HTTP_CODE" -eq 403 ]; then
                echo "::error::HTTP 403 means the bot doesn't have permission to send messages to this chat."
                echo "::error::Make sure the bot is added to the chat and has send message permissions."
              elif [ "$HTTP_CODE" -eq 400 ]; then
                echo "::error::HTTP 400 might indicate file is too large or invalid chat ID."
              fi
              return 1
            fi
          }
          
          echo "Searching for Universal APKs to send..."
          
          # Send Release APKs first
          if [ -n "${{ steps.verify-apks.outputs.release_path }}" ]; then
            echo "📤 Sending Release APKs..."
            for apk_file in "${{ steps.verify-apks.outputs.release_path }}"/*.apk; do
              if [ -f "$apk_file" ]; then
                send_apk "$apk_file" "release"
              fi
            done
          fi
          
          # Send Debug APKs
          if [ -n "${{ steps.verify-apks.outputs.debug_path }}" ]; then
            echo "📤 Sending Debug APKs..."
            for apk_file in "${{ steps.verify-apks.outputs.debug_path }}"/*.apk; do
              if [ -f "$apk_file" ]; then
                send_apk "$apk_file" "debug"
              fi
            done
          fi
          
          # Send summary message
          TOTAL_APKS=$(find app/build/outputs/apk -name "*.apk" 2>/dev/null | wc -l)
          SUMMARY_MESSAGE="🎉 *Build Complete!*%0A%0A📦 Successfully built and released *${TOTAL_APKS} Universal APKs*%0A🔖 Version: *${{ steps.build-info.outputs.version }}*%0A📅 Build Time: *${{ steps.build-info.outputs.build-time }}*%0A🏗️ Architecture: *Universal (compatible with all devices)*%0A✨ Optimizations: *R8, ProGuard, Bundle size reduction*%0A%0A🔗 [View Release on GitHub](https://github.com/${{ github.repository }}/releases/tag/${{ github.ref_name }})"
          
          curl -s -X POST \
            "https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/sendMessage" \
            -d chat_id="${TELEGRAM_CHAT_ID}" \
            -d text="$SUMMARY_MESSAGE" \
            -d parse_mode=Markdown \
            -d disable_web_page_preview=false > /dev/null
          
          echo "::notice title=Telegram Complete::📱 All Universal APKs sent to Telegram successfully!"
          echo "::endgroup::"
