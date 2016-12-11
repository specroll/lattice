(ns specroll.lattice.alfa.api
  (:require [clojure.spec :as s]
            [#?(:clj clojure.spec.gen
                :cljs cljs.spec.impl.gen) :as gen]
            [specroll.lattice.alfa.extensions :as extensions]
            [specroll.lattice.alfa.impl :as l :refer [$->]]))


(defn region
  ([tree] (region {} tree))
  ([opts tree]
   (-> (if-not (= :lattice/region (first tree))
         [:lattice/region tree]
         tree)
       (l/normalize-tree)
       (l/resolve-implementations)
       (first)
       (update :opts merge opts))))

(defn region-db [region]
  (->> region
       (tree-seq #(-> % :impl :region?)
                 #(-> % :impl :child-ui-nodes))
       (into {}
             (keep (fn [{:keys [opts]}]
                     (when-let [id (:lattice/id opts)]
                       [id opts]))))))

($-> specroll.lattice.specs
  (s/def :$/ui-id
    (s/with-gen #(and (keyword? %) (namespace %))
      (fn []
        (gen/fmap #(keyword "test.ui-id" %)
                  (gen/such-that #(not= % "")
                                 (gen/string-alphanumeric))))))

  (s/def :lattice/id :$/ui-id)

  (s/def :$/ui-opts
    (s/keys :req [:lattice/id]))

  (s/def :$/region :$/tree-node-resolved)

  (s/fdef region
    :args (s/cat :opts (s/? (s/nilable (s/spec :$/ui-opts)))
                 :tree (s/spec :$/tree))
    :ret :$/region)

  (s/fdef region-db
    :args (s/cat :region :$/region)
    :ret (s/every-kv :$/ui-id :$/ui-opts)))