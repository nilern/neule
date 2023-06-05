(ns neule.core
  (:refer-clojure :exclude [doseq for])
  (:require [neule.impl :as impl]))


;;; TODO: Proper error messages


(defn- reduce-right [f acc coll]
  (if-some [[v & vs] (seq coll)]
    (f v (reduce-right f acc vs))
    acc))


(defn- parse-xforms [bindings]
  (loop [xforms (transient []), bindings bindings]
    (if bindings
      (let [op (first bindings)]
        (if (keyword? op)
          (case op
            (:let :while :when)
            (let [bindings (or (next bindings)
                               (assert false))
                  arg (first bindings)]
              (recur (conj! xforms [op arg])
                     (next bindings)))

            (assert false))
          [(persistent! xforms) bindings]))
      [(persistent! xforms) bindings])))


(defn- parse-binding [bindings]
  (let [binder (first bindings)
        bindings (or (next bindings)
                     (assert false))

        expr (first bindings)
        bindings (next bindings)]
    (let [[xforms bindings] (parse-xforms bindings)]
      [[binder expr xforms] bindings])))


(defn- parse-bindings [bindings]
  (loop [parseds (transient []), bindings (seq bindings)]
    (if bindings
      (let [[parsed bindings] (parse-binding bindings)]
        (recur (conj! parseds parsed) bindings))
      (do
        (assert (empty? bindings))
        (persistent! parseds)))))


(defn- apply-doseq-xform [[op arg] body]
  (case op
    :let `(let ~arg ~body)
    :when `(when ~arg ~body)
    :while `(if ~arg ~body (reduced nil))))


(defn- emit-doseq [[[binder expr xforms] & bindings] body]
  (let [body (if (seq bindings)
               (emit-doseq bindings body)
               `(do ~@body))
        body (reduce-right apply-doseq-xform body xforms)]
    `(run! (fn [~binder] ~body) ~expr)))


(defmacro doseq [bindings & body]
  (assert (seq bindings))
  (let [bindings (parse-bindings bindings)]
    (emit-doseq bindings body)))


(defn- apply-for-xform [[op arg] body]
  (case op
    :let `(let ~arg ~body)
    :when `(if ~arg ~body impl/skip)
    :while `(if ~arg ~body impl/done)))


(defn- emit-for [[[binder expr xforms] & bindings] body]
  (let [outer (boolean (seq bindings))
        body (if outer (emit-for bindings body) body)
        body (reduce-right apply-for-xform body xforms)
        ;; OPTIMIZE: If just `:let`s, impl/conditional- not actually required:
        seq-fn (if outer
                 (if (seq xforms)
                   `impl/conditional-mapcat
                   `mapcat)
                 (if (seq xforms)
                   `impl/conditional-map
                   `map))]
    `(~seq-fn (fn [~binder] ~body) ~expr)))


(defmacro for [bindings body]
  (assert (seq bindings))
  (let [bindings (parse-bindings bindings)]
    (emit-for bindings body)))

;;; TODO: Comprehending into maps, vectors, sets?
