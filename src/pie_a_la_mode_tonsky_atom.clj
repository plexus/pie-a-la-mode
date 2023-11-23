(ns pie-a-la-mode-tonsky-atom
  "https://mastodon.online/@nikitonsky/111449865352557889")

;; Concurrency example from The Pragmatic Programmers. Waiters check if there's
;; enough pie and ice cream before they sell an order, but if they don't
;; coordinate it's still possible to sell more than we have.
;;
;; The below code should return either :ok or :not-available for a given order.
;; If we oversell, then it will throw an exception.
;;
;; How would you change this to ensure that that doesn't happen?

(def recipes
  {:pie {:pie 1}
   :ice-cream {:scoop 2}
   :pie-a-la-mode {:pie 1 :scoop 1}})

;; Inventory
(def *inventory
  (atom
   {:pie 12
    :scoop 50}
   :validator
   (fn [m] (every? #(>= % 0) (vals m)))))

(defn random-order [] ;; => {:ice-cream 1}
  {(rand-nth [:pie :ice-cream :pie-a-la-mode]) (inc (rand-int 3))})

(defn order->ingredients [order] ;; {:pie-a-la-mode 2} => {:pie 2 :scoop 2}
  (apply merge-with +
         (map (fn [[item qty]]
                (update-vals (get recipes item) (partial * qty))) order)))

(defn prepare [inventory order]
  (let [ingridients (order->ingredients order)]
    (merge-with - inventory ingridients)))

(defn handle-order!
  "Check if we have enough ingredients for the order, if so we prepare it"
  [order]
  (try
    (swap! *inventory prepare order)
    :ok
    (catch Exception e
      :not-available)))

;; The actual "simulation", handle 100 orders, but do them in futures so they
;; get handled on separate threads. Deref the futures after they have all been
;; created, so we can see any exceptions.
(run!
 deref
 (for [_ (range 100)]
   (future
     (let [order  (random-order)
           result (handle-order! order)]
       ;; Semafore while printing, to prevent the output from being mangled
       (locking *out*
         (println order "--->" result))))))
