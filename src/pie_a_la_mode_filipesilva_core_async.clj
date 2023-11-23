(ns pie-a-la-mode-filipesilva-core-async
  "https://www.reddit.com/r/Clojure/comments/180d4kv/comment/ka5wgk7/?utm_source=reddit&utm_medium=web2x&context=3"
  (:require [clojure.core.async :as a]))

;; Concurrency example from The Pragmatic Programmers. Waiters check if there's
;; enough pie and ice cream before they sell an order, but if they don't
;; coordinate it's still possible to sell more than we have.
;;
;; The below code should return either :ok or :not-available for a given order.

(def recipes
  {:pie {:pie 1}
   :ice-cream {:scoop 2}
   :pie-a-la-mode {:pie 1 :scoop 1}})

;; Inventory
(def pieces-of-pie       (->> :pie   (repeat 12) a/to-chan))
(def scoops-of-ice-cream (->> :scoop (repeat 50) a/to-chan))

(defn random-order [] ;; => {:ice-cream 1}
  {(rand-nth [:pie :ice-cream :pie-a-la-mode]) (inc (rand-int 3))})

(defn order->ingredients [order] ;; {:pie-a-la-mode 2} => {:pie 2 :scoop 2}
  (apply merge-with +
         (map (fn [[item qty]]
                (update-vals (get recipes item) (partial * qty))) order)))

(defn take-ingredient! [item]
  (case item
    :pie   (a/<!! pieces-of-pie)
    :scoop (a/<!! scoops-of-ice-cream)))

(defn return-ingredient! [item]
  (case item
    :pie   (a/>!! pieces-of-pie :pie)
    :scoop (a/>!! scoops-of-ice-cream :scoop)
    nil    nil))

(defn handle-order!
  "Check if we have enough ingredients for the order, if so we prepare it"
  [order]
  (let [taken-ingredients
        (->> order
             order->ingredients
             (mapcat (fn [[item qty]]
                       (repeat qty item)))
             (reduce #(conj %1 (take-ingredient! %2)) []))]
    (if (some nil? taken-ingredients)
      (do
        (run! return-ingredient! taken-ingredients)
        :not-available)
      :ok)))

;; The actual "simulation", handle 100 orders, but do them in futures so they
;; get handled on separate threads. Deref the futures after they have all been
;; created, so we can see any exceptions.
(run!
 deref
 (for [_ (range 100)]
   (future
     (let [order (random-order)
           result (handle-order! order)]
       ;; Semafore while printing, to prevent the output from being mangled
       (locking *out*
         (println order "--->" result))))))
