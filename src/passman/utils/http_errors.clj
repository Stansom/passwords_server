(ns passman.utils.http-errors)

(def errors
  {:auth-error {:status 401 :body (str :not-authorized)}})