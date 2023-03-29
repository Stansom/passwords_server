(ns passman.routes
  "Sketching some enhancement for ruuter routes
   Trying to make the routes map more simple to
   describe routes in one map.

   Before: 
   (def routes [{:path \"/register\"
			              :method :post
			              :response (str \" Posted \" r)}

			             {:path \"/register\"
			              :method :get
			              :response (str \" Get \" r)}

			             {:path \"/list\"
			              :method :get
			              :response (fn [req]
			                          req)}])
   
   After:
   (def ehn-routes [{:path \"/register\"
                  			:methods {:post (fn [r] (str \" Posted \" r))
                            			:get (fn [r] (str \" Get \" r))}}

			                 {:path \"/list\"
			                  :methods {:get (fn [req] req)}}])
   ")

(defn routes-builder [r]
  (vec (for [rt r
             mts (:methods rt)
             :let [k (key mts)]]
         {:path (:path rt)
          :method k
          :response (second mts)})))

(comment
  "some simple testing"
  (let [rts [{:path "/"
              :methods {:post (str "Posted ")
                        :get (str "Get ")}}
             {:path "/list"
              :methods {:get (str "Listing... ")
                        :post (str "Posting... ")
                        :patch (str "Patching... ")}}]]
    (assert (= (routes-builder rts)
               [{:path "/", :method :post, :response "Posted "}
                {:path "/", :method :get, :response "Get "}
                {:path "/list", :method :get, :response "Listing... "}
                {:path "/list", :method :post, :response "Posting... "}
                {:path "/list", :method :patch, :response "Patching... "}]))
    ;;
    ))