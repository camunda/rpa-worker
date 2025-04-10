#!/bin/bash
#
# 1) Signs executables (Mach-O, .app).
# 2) Builds signed.zip using ditto.
# 3) Notarizes signed.zip with xcrun notarytool submit.
#
# Usage:
#   ./sign_and_notarize.sh <SIGN_DIR> \
#       "<Developer ID Application: Cert (TEAMID)>" <AppleIDEmail> <AppPassword> <TeamID>
#
# Example:
#   ./sign_and_notarize.sh ./c8run \
#       "Developer ID Application: Camunda Services GmbH (TEAMID)" \
#       "you@example.com" "abcd-wxyz" "TEAMID1234"
#

set -e  # exit on error

if [ "$#" -ne 5 ]; then
  echo "Usage: $0 <SIGN_DIR> \"Developer ID Application: Cert (TEAMID)\" <AppleIDEmail> <AppPassword> <TeamID>"
  exit 1
fi

SIGN_DIR="$1"
CERT_NAME="$2"
APPLE_ID="$3"
APPLE_PASS="$4"
APPLE_TEAM="$5"

# Ensure c8run exists
if [ ! -d "$SIGN_DIR" ]; then
  echo "Error: '$SIGN_DIR' is not a directory."
  exit 1
fi


##############################################
# A) Functions to sign Mach-O
##############################################
sign_macho_in_folder() {
  local folder="$1"
  local signed_count=0

  echo "  -> Scanning for Mach-O/.app in: $folder"
  while IFS= read -r -d '' candidate; do
    echo "Candidate $candidate"

    # If .app
    if [ -d "$candidate" ] && [[ "$candidate" == *.app ]]; then
      echo "    Found .app: $candidate"
      if codesign --force --deep --options runtime --timestamp --sign "$CERT_NAME" "$candidate"; then
        ((signed_count++))
      else
        echo "[Error] .app sign failed: $candidate"
      fi
      continue
    fi

    # If Mach-O file
    if [ -f "$candidate" ]; then
      if [[ "$candidate" == *.app/* ]]; then
        continue  # skip inside .app
      fi
      if file -b "$candidate" | grep -q "Mach-O"; then
        echo "    Signing Mach-O: $candidate"
        if codesign --verbose=4 --force --options runtime --timestamp --sign "$CERT_NAME" "$candidate"; then
          ((signed_count++))
        else
          echo "[Error] Mach-O sign failed: $candidate"
        fi
      fi
    fi

  done < <(find "$folder" -print0)

  echo "  -> Signed $signed_count item(s) in $folder"
}


##############################################
# B) Sign binaries
##############################################
echo "=== Step B: Signing binary content ==="
sign_macho_in_folder "$SIGN_DIR"

##############################################
# C) Create signed.zip with ditto
##############################################
echo "=== Step C: Creating signed.zip with ditto (excluding removed items) ==="
(
  cd "$(dirname "$SIGN_DIR")"
  /usr/bin/ditto -c -k --keepParent "$(basename "$SIGN_DIR")" signed.zip
)
SIGNED_ZIP_PATH="$(cd "$(dirname "$SIGN_DIR")" && pwd -P)/signed.zip"
echo "Created: $SIGNED_ZIP_PATH"

##############################################
# D) Notarize signed.zip
##############################################
echo "=== Step D: Notarizing signed.zip ==="
xcrun notarytool submit "$SIGNED_ZIP_PATH" \
  --apple-id "$APPLE_ID" \
  --password "$APPLE_PASS" \
  --team-id "$APPLE_TEAM" \
  --wait
echo "Notarization succeeded (status: Accepted)"

