(ns passman.system)

(defonce system (atom {:port nil
                       :server nil
                       :session-keys #{}}))

(defn reset-session-keys! []
  (swap! system assoc-in [:session-keys] #{}))

(defn persist-session-token! [sys t]
  (swap! sys update-in [:session-keys] conj t))

(defn remove-session-token! [sys t]
  (swap! sys update-in [:session-keys] disj t))

(defn check-session-token [sys t]
  (get-in @sys [:session-keys t]))

(comment
  @system
  (reset-session-keys!)
  (persist-session-token! system "8b5100074504c501f665b04879d0554700a6870d8c299d4590b0ea2c86aa2750")

  ;;
  )