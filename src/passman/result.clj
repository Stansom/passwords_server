(ns passman.result)

(defn ok [v]
  {:ok v})

(defn err [v]
  {:err v})

(defn ok? [r]
  (when (map? r)
    (contains? r :ok)))


(defn err? [r]
  (when (map? r)
    (contains? r :err)))

(defn map-ok [r f]
  (if (ok? r)
    {:ok (f (:ok r))}
    r))

(defn flat-map-ok [r f]
  (if (ok? r)
    (f (:ok r))
    r))

(defn map-err [r f]
  (if (err? r)
    {:err (f (:err r))}
    r))

(defn flat-map-err [r f]
  (if (err? r)
    (f (:err r))
    r))

(defn of [v pred]
  (if (pred v)
    (ok v)
    (err v)))

(comment
  (of (get {:x 5} :a) #(and (not (nil? %)) (> % 10))))

