(ns selene.app
  (:require [reagent.core :as r]
            [reagent.dom :as rdom]
            [clojure.string :as str]
            [cljc.java-time.zoned-date-time :as zdt]
            [cljc.java-time.zone-id :as zone-id]
            [cljc.java-time.local-date :as ld]
            [cljc.java-time.temporal.iso-fields :as iso]
            [io.olympos.selene.moon :as moon]
            ["@js-joda/core"]
            ["@js-joda/timezone"]))

(def now (zdt/now))
(def year (r/atom (zdt/get-year now)))

(defn year-picker []
  [:div.year-picker
   [:button.year-change {:on-click #(swap! year dec)} "< " (dec @year)]
   [:div.this-year @year]
   [:button.year-change {:on-click #(swap! year inc)} (inc @year) " >"]])

(def zone (r/atom (zdt/get-zone now)))

(def zone-options (->> (zone-id/get-available-zone-ids)
                       sort
                       vec))

(def zone-set (set zone-options))

(def zone-shortcuts [["New York" "America/New_York"]
                     ["London" "Europe/London"]
                     ["Tokyo" "Asia/Tokyo"]
                     ["Sydney" "Australia/Sydney"]])

(defn zone-picker []
  [:div.zone-picker
   (doall
     (for [[title zid] zone-shortcuts
           :when (contains? zone-set zid)]
       ^{:key zid}
       [:button {:on-click #(reset! zone (zone-id/of zid))
                 :disabled (= zid (.id @zone))}
        title]))
   [:select {:value @zone
             :on-change #(reset! zone (zone-id/of (.. % -target -value)))}
    (doall
      (for [val zone-options]
        ^{:key val} [:option val]))]])

(defn month-name [day]
  (str/capitalize (.name (ld/get-month day))))

(defn year-days []
  (let [y @year]
    (->> (ld/of y 1 1)
         (iterate #(ld/plus-days % 1))
         (take-while #(= y (ld/get-year %)))
         (partition-by ld/get-month))))

(defn get-iso-week [day]
  (ld/get day iso/week-of-week-based-year))

(defn is-today? [day]
  (= day (zdt/to-local-date now)))

(def moon-emojis {::moon/new "🌑"
                  ::moon/first-quarter "🌓"
                  ::moon/full "🌕"
                  ::moon/last-quarter "🌗"})

(defn moon-phase [ld]
  (moon-emojis
    (moon/phase-change-on-date (ld/at-start-of-day ld @zone))))

(defn render-week [days]
  (let [day1 (first days)
        padding (map (fn [x] ^{:key [:empty x]}  [:td])
                     (range (- 7 (count days))))]
    ^{:key (get-iso-week day1)}
    [:tr
     (when (= 1 (ld/get-day-of-month day1))
       padding)
     (doall
      (for [day days]
        (let [dom (ld/get-day-of-month day)]
          ^{:key dom} [:td {:class [(when (is-today? day) "today")]}
                       (or (moon-phase day)
                           dom)])))
     (when (not= 1 (ld/get-day-of-month day1))
       padding)]))

(defn render-month [days]
  [:div.month
   [:div.month-name (month-name (first days))]
   ;; tried to do something sensible with the table itself, but couldn't get it
   ;; to avoid stretching the row heights, so...
   [:div.month-table-wrapper
    [:table
     [:thead>tr
      (map (fn [day] ^{:key day} [:td day])
           ["Mon" "Tue" "Wed" "Thu" "Fri" "Sat" "Sun"])]
     [:tbody
      (->> days
           (partition-by get-iso-week)
           (map render-week)
           doall)]]]])

(defn render-calendar []
  [:div.months
    (for [days (year-days)
          :let [month (ld/get-month (first days))]]
      ^{:key month} [render-month days])])

(defn app []
  [:<>
   [:h1 "Moon phases for " @year]
   [zone-picker]
   [year-picker]
   [render-calendar]])

(defn ^:dev/after-load run []
  (rdom/render [app] (js/document.getElementById "app")))
