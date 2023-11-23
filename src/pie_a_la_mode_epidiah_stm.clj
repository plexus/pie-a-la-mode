(ns pie-a-la-mode-epidiah-stm
  "https://dice.camp/@epidiah/111449212829319083")

;; Concurrency example from The Pragmatic Programmers. Waiters check if there's
;; enough pie and ice cream before they sell an order, but if they don't
;; coordinate it's still possible to sell more than we have.
;;
;; The below code should return either :ok or :not-available for a given order.
;; If we oversell, then it will throw an exception.
;;
;; I tried `ref`s for this since I've never used before.

(def recipes
  {:pie {:pie 1}
   :ice-cream {:scoop 2}
   :pie-a-la-mode {:pie 1 :scoop 1}})

;; Inventory
(def pieces-of-pie-left (ref 12))
(def scoops-of-ice-cream-left (ref 50))

(defn random-order [] ;; => {:ice-cream 1}
  {(rand-nth [:pie :ice-cream :pie-a-la-mode]) (inc (rand-int 3))})

(defn order->ingredients [order] ;; {:pie-a-la-mode 2} => {:pie 2 :scoop 2}
  (apply merge-with +
         (map (fn [[item qty]]
                (update-vals (get recipes item) (partial * qty))) order)))

(defn enough-inventory? [quantities] ;; {:pie 2 :scoop 3} => true
  (every? (fn [[item qty]]
            (case item
              :pie (<= qty @pieces-of-pie-left)
              :scoop (<= qty @scoops-of-ice-cream-left)))
          quantities))

(defn prepare-order!
  "Takes the needed ingredients out of the inventory, then verifies that inventory
  doesn't go below zero. Either returns :ok, or throws."
  [order]
  (doseq [[item qty] (order->ingredients order)]
    (case item
      :pie (alter pieces-of-pie-left - qty)
      :scoop (alter scoops-of-ice-cream-left - qty)))
  (when (< @pieces-of-pie-left 0)
    (throw (ex-info "Sold too much pie" {})))
  (when (< @scoops-of-ice-cream-left 0)
    (throw (ex-info "Sold too ice cream" {})))
  :ok)

(defn handle-order!
  "Check if we have enough ingredients for the order, if so we prepare it"
  [order]
  (dosync (if (enough-inventory? (order->ingredients order))
            (prepare-order! order)
            :not-available)))

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
