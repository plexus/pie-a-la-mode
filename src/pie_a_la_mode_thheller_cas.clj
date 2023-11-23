(ns pie-a-la-mode-thheller-cas
  "Version sent in by Thomas Heller, using `compare-and-set!`

   https://www.reddit.com/r/Clojure/comments/180d4kv/comment/ka6lbxm/?utm_source=reddit&utm_medium=web2x&context=3
  "
  (:import [java.util.concurrent Executors]))

;; Concurrency example from The Pragmatic Programmers. Waiters check if there's
;; enough pie and ice cream before they sell an order, but if they don't
;; coordinate it's still possible to sell more than we have.
;;
;; The below code should return either :ok or :not-available for a given order.
;; If we oversell, then it will throw an exception.
;;
;; How would you change this to ensure that that doesn't happen?

(def recipes
  {:pie {:piece-of-pie 1}
   :ice-cream {:scoop-of-ice-cream 2}
   :pie-a-la-mode {:piece-of-pie 1 :scoop-of-ice-cream 1}})

(defn random-order [_] ;; => {:ice-cream 1}
  {(rand-nth [:pie :ice-cream :pie-a-la-mode])
   (inc (rand-int 3))})

(defn safe+ [x y]
  (if (nil? x)
    y
    (+ x y)))

(defn order->ingredients [order] ;; {:pie-a-la-mode 2} => {:pie 2 :scoop 2}
  (reduce-kv
   (fn [result recipe amount]
     (reduce-kv
      (fn [result ingredient ingredient-amount]
        (update result ingredient safe+ (* amount ingredient-amount)))
      result
      (get recipes recipe)
      ))
   {}
   order))

(defn handle-order!
  [inventory-ref order]
  (let [required-ingredients
        (order->ingredients order)]

    (loop [attempts 0]
      ;; attempt to fulfill order by taking each ingredient out of inventory
      (let [state
            @inventory-ref

            next-state
            (reduce-kv
             (fn [state ingredient amount]
               (let [avail (get state ingredient)]
                 (if (< avail amount)
                   (reduced ::not-enough-stuff)
                   (update state ingredient - amount))))
             state
             required-ingredients)]

        (cond
          ;; ::not-enough-stuff
          (keyword? next-state)
          (assoc order :not-enough-stuff true)

          ;; inventory had enough, see if someone else took something while we did
          ;; if not our order goes through successfully
          ;; FIXME: this is of course silly and not something you'd do in real life
          (compare-and-set! inventory-ref state next-state)
          (assoc order :ingredients-used required-ingredients :attempts-made attempts)

          ;; someone else took something, try again
          :else
          (recur (inc attempts))
          )))))


(comment
  (random-order 1)
  (order->ingredients (random-order 1)))

;; assuming 5 waiters to take orders
(def waiters
  (Executors/newFixedThreadPool 5))

(def inventory-ref
  (atom
   {:piece-of-pie 12
    :scoop-of-ice-cream 50}))

;; enqueue all orders, let waiters work them off
(def all-orders
  (->> (range 100)
       (map random-order)
       (map (fn [order]
              (.submit waiters (fn [] (handle-order! inventory-ref order)))))
       (into [])))

;; get the results
(println "==== order results")
(doseq [p all-orders
        :let [result @p]]
  (prn result))

(println "==== remaining inventory")
(prn @inventory-ref)

(.shutdownNow waiters)
