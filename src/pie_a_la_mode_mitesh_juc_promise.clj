(ns pie-a-la-mode-mitesh-juc-promise)

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
(def pieces-of-pie-left (volatile! 12))
(def scoops-of-ice-cream-left (volatile! 50))

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
      :pie (vswap! pieces-of-pie-left - qty)
      :scoop (vswap! scoops-of-ice-cream-left - qty)))
  (when (< @pieces-of-pie-left 0)
    (throw (ex-info "Sold too much pie" {})))
  (when (< @scoops-of-ice-cream-left 0)
    (throw (ex-info "Sold too ice cream" {})))
  :ok)

(defn handle-order!
  "Check if we have enough ingredients for the order, if so we prepare it"
  [order]
  (if (enough-inventory? (order->ingredients order))
    (prepare-order! order)
    :not-available))

(def pending-orders (java.util.concurrent.LinkedBlockingDeque. 1000))
(def table-waiter (volatile! {}))

(def terminate (volatile! false))

(defonce head-chef
  (Thread.
   (fn []
     (while (not @terminate)
       (let [order (.take pending-orders)]
         (println "Took order: " order)
         (let [{:keys [table order result]} order
               status (handle-order! order)]
           ;; (println "Served: " table)
           (deliver result {:table table :status status})
           ;; (println "Added: " table)
           ))))))


(comment
  (.start head-chef)
  (.interrupt head-chef)
  (vswap! table-waiter assoc 1 (java.util.concurrent.LinkedBlockingQueue. 2))
  @table-waiter
  (get @table-waiter 4)
  (sort (keys @table-waiter))
  (.put pending-orders {:table 1 :order {:pie 2}})
  (println (.peek pending-orders))

  )

#_(defn prepare-order!
    "Takes the needed ingredients out of the inventory, then verifies that inventory
  doesn't go below zero. Either returns :ok, or throws."
    [order]
    (loop [try 0]
      (println "Try: " try)
      (let [pie-left @pieces-of-pie-left
            scoops-left @scoops-of-ice-cream-left
            stock (reduce (fn [stock [item qty]]
                            (update stock item - qty))
                          {:pie pie-left :scoop scoops-left}
                          (order->ingredients order))]
        (cond
          (< (:pie stock) 0)
          (recur (inc try))
          (< (:scoop stock) 0)
          (recur (inc try))
          :else
          (do
            (let [pie (compare-and-set! pieces-of-pie-left pie-left (:pie stock))]
              (if pie
                (let [scoops (compare-and-set! scoops-of-ice-cream-left scoops-left (:scoop stock))]
                  :ok)
                (do

                  :not-available)
                ))
            :ok)))))

#_(defn handle-order!
    "Check if we have enough ingredients for the order, if so we prepare it"
    [order]
    (locking pieces-of-pie-left
      (locking scoops-of-ice-cream-left
        (if (enough-inventory? (order->ingredients order))
          (prepare-order! order)
          :not-available))))

;; The actual "simulation", handle 100 orders, but do them in futures so they
;; get handled on separate threads. Deref the futures after they have all been
;; created, so we can see any exceptions.
(do
  (vreset! pieces-of-pie-left 12)
  (vreset! scoops-of-ice-cream-left 50)
  (run!
   deref
   (doall
    (for [table (range 100)]
      (do
        (println "Placing an order: " table)
        (future
          (let [order (random-order)
                result (promise)
                _ (.put pending-orders {:order order
                                        :table table
                                        :result result})
                result @result]
            (println "Result")
            ;; Semafore while printing, to prevent the output from being mangled
            (locking *out*
              (println "[" table "]" order "--->" result)))))))))

;; (prepare-order! {:ice-cream 10000})
