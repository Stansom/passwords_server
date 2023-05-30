(ns passman.utils.validate)

(defn check-pers
  "This code validates the presence of keys (ks) and, if all present, 
   invokes function (f)."
  [ks f]
  (if (and
       (seq ks)
       (every? seq ks))
    (f)
    {:err {:info "all keys must be presented"}}))