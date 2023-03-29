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

(comment

  ;; THE BEST SOLUTION FOR NOW
  (time (dotimes [_ 1000]
          (for [fst rts
                mts (:methods fst)
                :let [k (key mts)]]
            {:path (:path fst)
             :method k
             :response (second mts)})))
  (time (dotimes [_ 1000]
          (routes-builder rts)))

  (time (dotimes [_ 100000] (mapcat
                             (fn [rt]
                               (loop [#_#_rt rts
                                      r rt
                                      mt (:methods r)
                                      res []]
                                 (if-not (seq mt)
                                   res
                                   (recur r (rest mt) (conj res
                                                            {:path (-> r first second)
                                                             :method (ffirst mt)
                                                             :response (-> mt first second)})))))
                             rts)))

  (time (dotimes [_ 100000] (map
                             (fn [rt]
                               (loop [#_#_rt rts
                                      r rt
                                      mt (:methods r)
                                      res []]
                                 (if-not (seq mt)
                                   res
                                   (recur r (rest mt) (conj res
                                                            {:path (-> r first second)
                                                             :method (ffirst mt)
                                                             :response (-> mt first second)})))))
                             rts)))

  (map
   (fn [rt]
     (loop [#_#_rt rts
            r rt
            mt (:methods r)
            res []]
       (if-not (seq mt)
         res
         (recur r (rest mt) (conj res
                                  {:path (-> r first second)
                                   :method (ffirst mt)
                                   :response (-> mt first second)})))))
   rts)

  (into [] (flatten (loop [rt rts
                           #_#_r (first rt)
                           #_#_mt (:methods (first rt))
                           res []]
                      (if-not (seq rt)
                        res
                        (let [fst (first rt)
                              mt (:methods fst)]
                          (if-not (seq mt)
                            res
                            (let [sq (seq mt)
                                  resp (map (fn [x]
                                              {:path (-> fst first second)
                                               :method (first x)
                                               :response (second x)}) sq)]
                              (recur (rest rt) (conj res resp))))
                          #_(recur (rest rt) (conj res
                                                   {:path (-> (first rt) first second)
                                                    :method (ffirst mt)
                                                    :response (-> mt #_#_first second)})))))))

  #_(clojure.walk/prewalk-demo (fn [x] (if (number? x) (* 5 x) x)) [1 [2 [3 [4 5 6 [7]]]]])

  (mapcat
   (fn [rt]
     (loop [#_#_rt rts
            r rt
            mt (:methods r)
            res []]
       (if-not (seq mt)
         res
         (recur r (rest mt) (conj res
                                  {:path (-> r first second)
                                   :method (ffirst mt)
                                   :response (-> mt first second)})))))
   rts)

  #_(loop [rt rts
           #_#_r rt
           #_#_mt (:methods r)
           res []]
      (if-not (seq rt)
        res
        (let [frt (first rt)
              mt (:methods frt)]
          (for [m mt]
            (recur (rest rt) (conj res
                                   {:path (-> frt first second)
                                    :method m
                                    :response (-> mt first second)}))))))

  #_(reduce
     (fn [acc v])

     []
     {:path "/"
      :methods {:post (fn [r] (str "Posted" r))
                :get (fn [r] (str "Get" r))}})

  (map (fn [m]
         {:path (m :path)
          :method (m :methods)})
       [{:path "/"
         :methods {:post (fn [r] (str "Posted" r))
                   :get (fn [r] (str "Get" r))}}])

  #_(map-indexed

     (repeat (count (:methods (first rts))) (first rts)))

  (def d (map rand  (range 1 4096)))

  (time (dotimes [_ 3000000] (doall
                              (map #(if (> % 0.5) (* % 0.99) (* % 1.01)) d))))

  (def items [{:id 1 :title "foo"}
              {:id 2 :title "bar"}])

  (defn links-builder [l]
    (for [rt l
          mts (:tag-id rt)
          :let [k (key mts)]]
      {:path (:path rt)
       :method k
       :response (second mts)}))

  (def links [{:note-id 1 :tag-id 2}
              {:note-id 1 :tag-id 3}
              {:note-id 2 :tag-id 1}
              {:note-id 2 :tag-id 2}])

  (group-by :note-id links)

  (update-vals {1 [{:note-id 1, :tag-id 2} {:note-id 1, :tag-id 3}],
                2 [{:note-id 2, :tag-id 1} {:note-id 2, :tag-id 2}]} #(mapv :tag-id %))

  (into {} (map (juxt first (comp #(mapv :tag-id %) second))
                {1 [{:note-id 1, :tag-id 2} {:note-id 1, :tag-id 3}],
                 2 [{:note-id 2, :tag-id 1} {:note-id 2, :tag-id 2}]}))

  ([{:note-id 1, :tag-id 2} {:note-id 1, :tag-id 3}]
   [{:note-id 2, :tag-id 1} {:note-id 2, :tag-id 2}])

  {1 [{:note-id 1, :tag-id 2} {:note-id 1, :tag-id 3}],
   2 [{:note-id 2, :tag-id 1} {:note-id 2, :tag-id 2}]}

  (into {} (map (comp (juxt :id identity)) items)))