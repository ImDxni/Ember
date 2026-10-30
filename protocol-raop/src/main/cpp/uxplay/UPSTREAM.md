# Vendored UxPlay

- Upstream: https://github.com/FDH2/UxPlay
- Commit: `534e2cc0e558d6c94bcc66964f8fc13ad73a6cdc` (2026-10-28)
- Imported: `lib/` (unmodified), `LICENSE`
- Not imported: `lib/dns_sd/`, `lib/mdnsd/` (replaced by `../android/dnssd_android.c`),
  `lib/**/CMakeLists.txt` (replaced by `../CMakeLists.txt`), `renderers/` (GStreamer),
  `uxplay.cpp` (its startup logic is ported to `../android/raop_jni.c`).

Do not edit files in this directory: re-run `scripts/update-uxplay.sh [commit]` instead.
Any unavoidable change must go in a patch under `protocol-raop/patches/` and be listed here.
