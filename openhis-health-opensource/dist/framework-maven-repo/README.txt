Whale Framework prebuilt Maven repo (obfuscated JAR, shipped with this repo).

Clone and build from repo root (no extra Maven profile required):
Do not delete this directory; Framework source is not public.

From repo root:

  mvn clean package -pl whale-health/health-app -am -DskipTests -Pbinary-framework,with-booking,with-report