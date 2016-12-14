(ns specroll.lattice.alfa.impl
  (:require [clojure.spec :as s]
            [clojure.string :as str]
            [clojure.walk :as walk]
            [specroll.lattice.alfa.extensions :as extensions]))


(defn ui-tag? [tag]
  (boolean (namespace tag)))

(defmethod extensions/region-ui-impl :default [_ _]
  nil)

(defn normalize-tree [raw-tree]
  (map (fn [node]
         (cond
           (string? node) node
           (symbol? node) node
           :default
           (let [[tag maybe-opts & children] node
                 opts (when (map? maybe-opts) maybe-opts)
                 children' (if (and (not opts)
                                    (or (symbol? maybe-opts)
                                        (seq maybe-opts)))
                             (into [maybe-opts] children)
                             children)]
             {:tag tag
              :opts opts
              :children (mapcat normalize-tree children')})))
       [raw-tree]))

(defn resolve-implementations [tree]
  (map #(walk/postwalk
         (fn [node]
           (if-let [tag (and (map? node)
                             (:tag node))]
             (if-not (ui-tag? tag)
               (assoc node :impl (extensions/dom-impl tag))
               (if-let [region-impl (extensions/region-ui-impl tag node)]
                 (-> node
                     (assoc :impl region-impl)
                     (update :opts assoc :lattice/resolved-tree (:children node)))
                 (assoc node :impl (extensions/ui-impl tag))))
             node))
         %)
       tree))

(defn collect-ui-nodes [tree]
  (->> tree
       (mapcat (fn [node]
                 (tree-seq #(not (get-in % [:impl :region?]))
                           :children
                           node)))
       (filter #(when-let [tag (:tag %)]
                  (ui-tag? tag)))))

(defn replace-opts-syms [props opts]
  (into {}
        (map (fn [[k v]]
               (if (symbol? v)
                 [k (get props (keyword v))]
                 [k v])))
        opts))

(defn replace-tree-syms [props tree]
  (map (fn [node]
         (cond
           (symbol? node)
           (get props (keyword node))

           (not (map? node)) node
           
           (symbol? (:tag node))
           (when-let [branch-props (get props (keyword (:tag node)))]
             ;; todo: enforce singular child in spec
             (first (replace-tree-syms (merge props branch-props)
                                       (:children node))))

           :default
           (-> node
               (update :opts #(replace-opts-syms props %))
               (update :children #(replace-tree-syms props %)))))
       tree))

