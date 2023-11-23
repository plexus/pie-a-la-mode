(ns pie-a-la-mode-johnny-stm
  "https://gist.github.com/JohnnyJayJay/69ef9c9f0092ad3a1d5082f0b71e66c7")

;; Concurrency example from The Pragmatic Programmers. Waiters check if there's
;; enough pie and ice cream before they sell an order, but if they don't
;; coordinate it's still possible to sell more than we have.
;;
;; This one should coordinate correctly!

(def recipes
  {:pie {:pie 1}
   :ice-cream {:scoop 2}
   :pie-a-la-mode {:pie 1 :scoop 1}})

;; Inventory
(def inventory
  {:pie (ref 12)
   :scoop (ref 50)})

(defn random-order [] ;; => {:ice-cream 1}
  {(rand-nth [:pie :ice-cream :pie-a-la-mode]) (inc (rand-int 3))})

(defn order->ingredients [order] ;; {:pie-a-la-mode 2} => {:pie 2 :scoop 2}
  (->> order
       (map (fn [[item qty]]
              (update-vals (get recipes item) (partial * qty))))
       (apply merge-with +)))

(defn enough-inventory? [quantities] ;; {:pie 2 :scoop 3} => true
  (every? (fn [[item qty]]
            (<= qty @(get inventory item)))
          quantities))

(defn handle-order!
  "Check if we have enough ingredients for the order, if so we prepare it"
  [order]
  (let [quantities (order->ingredients order)]
    (dosync
     (if (enough-inventory? quantities)
       (do
         (doseq [[item quantity] quantities]
           (alter (get inventory item) - quantity))
         :ok)
       :not-available))))

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
