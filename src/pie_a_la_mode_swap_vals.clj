(ns pie-a-la-mode-swap-vals
  "Version by plexus that uses swap-vals! to check if the order could be made or
  not.")

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
(def inventory
  (atom
   {:pie 12
    :scoop 50}))

(defn random-order [] ;; => {:ice-cream 1}
  {(rand-nth [:pie :ice-cream :pie-a-la-mode]) (inc (rand-int 3))})

(defn order->ingredients [order] ;; {:pie-a-la-mode 2} => {:pie 2 :scoop 2}
  (apply merge-with +
         (map (fn [[item qty]]
                (update-vals (get recipes item) (partial * qty))) order)))

(defn enough-inventory? [quantities] ;; {:pie 2 :scoop 3} => true
  (every? (fn [[item qty]]
            (<= qty (get @inventory item)))
          quantities))

(defn handle-order!
  "Check if we have enough ingredients for the order, if so we prepare it"
  [order]
  (let [[old new]
        (swap-vals! inventory
                    (fn [i]
                      (let [ingredients (order->ingredients order)]
                        (if (enough-inventory? ingredients)
                          (reduce (fn [i [item qty]]
                                    (update i item - qty))
                                  i
                                  ingredients)
                          i))))]
    (if (= old new)
      :not-available
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
       (locking *out* (println order "--->" result))))))

;; - locking
;; - STM -> ref/dosync/alter
;; - queue -> juc BlockingQueue
;;   - core.async
;; - atom
;;   - compare-and-set! (atom)
;;   - swap-vals!
;;   - validator
