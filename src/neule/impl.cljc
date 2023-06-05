(ns neule.impl)


;;; OPTIMIZE: `lazy-seq` instead of punting to `sequence`?
;;; OPTIMIZE: If `lazy-seq`, then chunk-aware.


(def skip #?(:clj (Object.), :cljs #js {}))


(def done #?(:clj (Object.), :cljs #js {}))


(defn conditional-map
  ([f]
   (fn [rf]
     (fn
       ([] (rf))
       ([acc] (rf acc))
       ([acc v]
        (let [v (f v)]
          (condp identical? v
            skip acc
            done (reduced acc)
            (rf acc v)))))))
  ([f coll] (sequence (conditional-map f) coll)))


(defn conditional-mapcat
  ([f] (comp (conditional-map f) cat))
  ([f coll] (sequence (conditional-mapcat f) coll)))
