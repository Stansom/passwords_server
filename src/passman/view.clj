(ns passman.view
  (:require [hiccup2.core :as html]
            [hiccup.page :as hp]))

(defn html-wrap [b]
  (hp/html5 (hp/include-css "https://unpkg.com/blocks.css/dist/blocks.min.css") b))

(defn root [_]
  (html-wrap (html/html {:mode :html}
                        [:body
                         {:style {:display "flex"
                                  :flex-direction "column"
                                  :width "100vw"
                                  :height "100vh"
                                  :align-items "center"
                                  :justify-content "center"}}
                         [:main {:style {:display "flex"
                                         :flex-direction "column"
                                         :width "50vw"
                                         :height "100vh"
                                         :align-items "center"
                                         :justify-content "center"}}
                          [:h1.block.fixed.accent "Choose your destiny:"]
                          [:div.buttons {:style {:display "flex"
                                                 :width "30%"
                                                 :justify-content "space-between"}}
                           [:a#login_button.block {:href "/view/login"
                                                   :style {:text-decoration "none"}} "Login"]
                           [:a#register_button.block {:href "/view/register"
                                                      :style {:text-decoration "none"}} "Register"]]]])))

(defn add-password []
  [:form#form__addpassword {:method :get
                            :action "/add"
                            :style {:display "flex"
                                    :flex-direction "column"
                                    :align-items "center"
                                    :justify-content "center"}}
   [:input#username.fixed.wrapper.block.input__login {:placeholder "Enter login here"
                                                      :name "login"
                                                      :value ""
                                                      :style {:width "35vw"
                                                              :height "35px"}}]
   [:input#pass.fixed.wrapper.block.input__url {:placeholder "Enter URL here"
                                                :name "url"
                                                :value ""
                                                :style {:width "35vw"
                                                        :height "35px"}}]
   [:input#pass.fixed.wrapper.block.input__password {:placeholder "Enter password here"
                                                     :name "urlpassword"
                                                     :value ""
                                                     :style {:width "35vw"
                                                             :height "35px"}}]
   [:button.block.send__register "ADD"]])

(defn list-passwords [u pm]
  (html-wrap (html/html {:mode :html} [:div {:style {:display "flex"
                                                     :flex-direction "column"
                                                     :width "100vw"
                                                     :height "100vh"
                                                     :align-items "center"
                                                     #_#_:justify-content "center"}}
                                       [:div.block.fixed {:style {:margin-top "12%" :font-size "36px"}} "Hello, " u "!"]

                                       (add-password)
                                       [:ul {:style {:list-style-type "none"
                                                     :display "flex"
                                                     :flex-direction "column"
                                                     :align-items "center"
                                                     :justify-content "center"
                                                     :width "60%"}}
                                        [:div {:style
                                               {:display "flex"
                                                :flex-direction "row"
                                                :width "70%"
                                                :height "50px"
                                                :justify-content "space-between"
                                                :align-items "center"}}
                                         [:li [:h4 "login"]] [:li [:h4 "password"]]]
                                        (map (fn [e] [:div {:style
                                                            {:display "flex"
                                                             :flex-direction "row"
                                                             :width "100%"
                                                             :height "50px"
                                                             :justify-content "space-between"
                                                             :align-items "center"
                                                             :border-top "1px solid black"}}
                                                      #_[:li {:style {:width "20%" :text-align "left"}} (:username e)]
                                                      [:li {:style {:width "50%" :text-align "left"}} (:url e)]
                                                      [:li {:style {:width "50%" :text-align "right"}} (:password e)]]) pm)]])))

(defn register [_]
  (html-wrap (html/html {:mode :html}
                        [:body
                         {:style {:display "flex"
                                  :flex-direction "column"
                                  :width "100vw"
                                  :height "100vh"
                                  :align-items "center"
                                  :justify-content "center"}}
                         [:main {:style {:display "flex"
                                         :flex-direction "column"
                                         :width "50vw"
                                         :height "100vh"
                                         :align-items "center"
                                         :justify-content "center"}}
                          [:h1.block.fixed.accent "Registration"]
                          [:form {:method :get
                                  :action ""
                                  :style {:display "flex"
                                          :flex-direction "column"
                                          :align-items "center"
                                          :justify-content "center"}}
                           [:input#username.fixed.wrapper.block.input__username {:placeholder "Enter your username here"
                                                                                 :name "username"
                                                                                 :value ""
                                                                                 :style {:width "35vw"
                                                                                         :height "35px"}}]
                           [:input#pass.fixed.wrapper.block.input__password {:placeholder "Enter password here"
                                                                             :name "password"
                                                                             :value ""
                                                                             :style {:width "35vw"
                                                                                     :height "35px"}}]
                           [:button.block.send__register {#_#_:href "/view/list"
                                                          #_#_:type "submit"
                                                          :style {:text-decoration "none"}} "register"]]]])))

(defn random-password [n]
  (html-wrap (html/html {:mode :html}
                        [:body
                         {:style {:display "flex"
                                  :flex-direction "column"
                                  :width "100vw"
                                  :height "100vh"
                                  :align-items "center"
                                  :justify-content "center"}}
                         [:main {:style {:display "flex"
                                         :flex-direction "column"
                                         :width "50vw"
                                         :height "100vh"
                                         :align-items "center"
                                         :justify-content "center"}}
                          [:h1.block.fixed.accent "Generate random password:"]
                          [:form {:method :get
                                  :style {:display "flex"
                                          :flex-direction "column"
                                          :align-items "center"
                                          :justify-content "center"}}
                           [:input#username.fixed.wrapper.block.input__username {:placeholder "Length of password"
                                                                                 :name "length"
                                                                                 :style {:width "35vw"
                                                                                         :height "35px"}}]

                           [:button.block.generate_pass {:style {:text-decoration "none"}} "generate"]]

                          [:div {:style {:border "3px solid black"
                                         :padding "4px 4px"}} n]]])))

(defn login [_] (html-wrap (html/html {:mode :html}
                                      [:body
                                       {:style {:display "flex"
                                                :flex-direction "column"
                                                :width "100vw"
                                                :height "100vh"
                                                :align-items "center"
                                                :justify-content "center"}}
                                       [:main {:style {:display "flex"
                                                       :flex-direction "column"
                                                       :width "50vw"
                                                       :height "100vh"
                                                       :align-items "center"
                                                       :justify-content "center"}}
                                        [:h1.block.fixed.accent "Login"]
                                        [:form#form__login {:method :get
                                                            :action "/view/list"
                                                            :style {:display "flex"
                                                                    :flex-direction "column"
                                                                    :align-items "center"
                                                                    :justify-content "center"}}
                                         [:input#username.fixed.wrapper.block.input__username {:placeholder "Enter your username here"
                                                                                               :name "username"
                                                                                               :value ""
                                                                                               :style {:width "35vw"
                                                                                                       :height "35px"}}]
                                         [:input#pass.fixed.wrapper.block.input__password {:placeholder "Enter password here"
                                                                                           :name "password"
                                                                                           :value ""
                                                                                           :style {:width "35vw"
                                                                                                   :height "35px"}}]
                                         [:button.block.send__register "login"]]]])))

(defn succeed-registration [u]
  (html-wrap (html/html {:mode :html}
                        [:body
                         {:style {:display "flex"
                                  :flex-direction "column"
                                  :width "100vw"
                                  :height "100vh"
                                  :align-items "center"
                                  :justify-content "center"}}
                         [:main {:style {:display "flex"
                                         :flex-direction "column"
                                         :width "50vw"
                                         :height "100vh"
                                         :align-items "center"
                                         :justify-content "center"}}
                          [:h1.block.fixed.accent "Registered!"]
                          [:div.registered
                           [:div.fixed.block "User " [:span {:style {:font-size "23px"
                                                                     :color "#2f69a7"}} u] " was succefully registered!"]]
                          [:div.buttons {:style {:display "flex"
                                                 :width "30%"
                                                 :justify-content "center"}}
                           [:a#login_button.block {:href "/view/login"
                                                   :style {:text-decoration "none"}} "Login"]]]])))