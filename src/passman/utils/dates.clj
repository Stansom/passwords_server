(ns passman.utils.dates)

(defn date-to-utc
  "Converts INST to UTC timezone"
  [inst]
  (-> (java.time.format.DateTimeFormatter/ofPattern "E, dd MMM yyyy HH:mm:ss z")
      (.withZone java.time.ZoneOffset/UTC)
      (.format inst)))

(defn hours-to-date
  "Adds hours (h) to present day"
  [h]
  (.plusMillis (java.time.Instant/now) (* 3600000 h)))