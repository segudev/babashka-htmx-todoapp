(require '[org.httpkit.server :as srv]
         '[clojure.java.browse :as browse]
         '[clojure.core.match :refer [match]]
         '[clojure.string :as str]
         '[hiccup.core :as h])

(import '[java.net URLDecoder])

(def port 3000)

(def todos (atom (sorted-map 1 {:id 1 :name "Taste htmx with Babashka" :done true}
                             2 {:id 2 :name "Buy a unicorn" :done false})))

(def todos-id (atom (count @todos)))

(defn add-todo! [name]
  (let [id (swap! todos-id inc)]
    (swap! todos assoc id {:id id :name name :done false})))

(defn toggle-todo! [id]
  (swap! todos update-in [(Integer. id) :done] not))

(defn remove-todo! [id]
  (swap! todos dissoc (Integer. id)))

(defn find-todo [id todos-list]
  (first (filter #(= (Integer. id) (:id %)) todos-list)))

(defn todo-item [{:keys [id name done]}]
  [:li {:id (str "todo-" id)
        :class (when done "completed")}
   [:div.view
    [:input.toggle {:hx-patch (str "/todos/" id)
                    :type "checkbox"
                    :checked done
                    :hx-target (str "#todo-" id)
                    :hx-swap "outerHTML"}]
    [:label {:hx-get (str "/todos/edit/" id)
             :hx-target (str "#todo-" id)
             :hx-swap "outerHTML"} name]
    [:button.destroy {:hx-delete (str "/todos/" id)
                      :_ (str "on htmx:afterOnLoad remove #todo-" id)}]]])

(defn template []
  {:status 200
   :body
   (str
    "<!DOCTYPE html>"
    (h/html
     [:head
      [:meta {:charset "UTF-8"}]
      [:title "Htmx + Babashka"]
      [:link {:href "https://unpkg.com/todomvc-app-css@2.4.1/index.css" :rel "stylesheet"}]
      [:script {:src "https://unpkg.com/htmx.org@1.5.0/dist/htmx.min.js" :defer true}]
      [:script {:src "https://unpkg.com/hyperscript.org@0.8.1/dist/_hyperscript.min.js" :defer true}]]
     [:body
      [:section.todoapp
       [:header.header
        [:h1 "todos"]
        [:form {:hx-post "/todos"
                :hx-target "#todo-list"
                :hx-swap "afterbegin"
                :_ "on htmx:afterOnLoad set #txtTodo.value to ''"}
         [:input#txtTodo.new-todo {:name "todo"
                                   :placeholder "What needs to be done?"
                                   :autofocus ""}]]]
       [:section.main

        [:input#toggle-all.toggle-all {:type "checkbox"}]
        [:label {:for "toggle-all"} "Mark all as complete"]]
       [:ul#todo-list.todo-list
        (for [todo @todos]
          (todo-item (val todo)))]]]))})

(defn add-item [req]
  (let [name (-> req
                 :body
                 slurp
                 (str/split #"=")
                 second
                 URLDecoder/decode)
        todo (add-todo! name)]
    (h/html (todo-item (val (last todo))))))

(defn edit-item [id]
  (let [{:keys [id name]} (get @todos (Integer. id))]
    (h/html
     [:form {:hx-post (str"/todos/update/" id)}
      [:input.edit {:type "text"
                    :name "name"
                    :value name}]])))

(defn update-item [req id]
  (let [name (-> req
                 :body
                 slurp
                 (str/split #"=")
                 second
                 URLDecoder/decode)
        todo (swap! todos assoc-in [(Integer. id) :name] name)]
    (h/html (todo-item (get todo (Integer. id))))))

(defn patch-item
  [id]
  (let [todo (toggle-todo! id)]
    (h/html (todo-item (get todo (Integer. id))))))

(defn delete-item [id]
  (remove-todo! id))

(defn routes [{:keys [request-method uri] :as req}]
  (let [path (vec (rest (str/split uri #"/")))]
    (match [request-method path]
           [:get []] (template)
           [:get ["todos" "edit" id]] {:body (edit-item id)}
           [:post ["todos"]] {:body (add-item req)}
           [:post ["todos" "update" id]] {:body (update-item req id)}
           [:patch ["todos" id]] {:body (patch-item id)}
           [:delete ["todos" id]] {:body (delete-item id)}
           :else {:status 404 :body "Error 404: Page not found"})))

(let [url (str "http://localhost:" port "/")]
  (srv/run-server #'routes {:port port})
  (println "serving" url)
  (browse/browse-url url)
  @(promise))