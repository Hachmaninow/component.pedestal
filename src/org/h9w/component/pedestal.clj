; The MIT License (MIT)
;
; Copyright © 2016 Stuart Sierra
;
; Permission is hereby granted, free of charge, to any person obtaining a copy of this software
; and associated documentation files (the "Software"), to deal in the Software without restriction,
; including without limitation the rights to use, copy, modify, merge, publish, distribute,
; sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
; furnished to do so, subject to the following conditions:
;
; The above copyright notice and this permission notice shall be included in all copies or
; substantial portions of the Software.
;
; THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING
; BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
; NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
; DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
; OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.


;
; This work is mainly based on https://github.com/stuartsierra/component.pedestal.
; The fork has been created out of the need to provide the functionality as a library.
;
; Apart from this I fixed one or two minor issues I had when integrating that with the
; Pedestal version I use.
;
; Aah... and I changed the namespace because it felt a little weird not to match the library
; artifact in any way ;-).
;

(ns org.h9w.component.pedestal
  "Connection between the Component framework and the Pedestal web application server."
  (:require [com.stuartsierra.component :as component]
            [io.pedestal.http :as http]
            [io.pedestal.interceptor :refer [interceptor
                                             interceptor-name]]))

(defn insert-context-interceptor
  "Returns an interceptor which associates key with value in the Pedestal context map."
  [key value]
  (interceptor
    {:name  ::insert-context
     :enter (fn [context] (assoc context key value))}))

(defn add-component-interceptor
  "Adds an interceptor to the pedestal-config map which associates the
  pedestal-component into the Pedestal context map. Must be called
  before io.pedestal.http/create-server."
  [pedestal-config pedestal-component]
  (update pedestal-config
    ::http/interceptors
    conj
    (insert-context-interceptor
      ::pedestal-component
      pedestal-component)))

(defrecord Pedestal [pedestal-config pedestal-server]
  component/Lifecycle
  (start [this]
    (if pedestal-server
      this
      (assoc this :pedestal-server
                  (-> pedestal-config
                    (add-component-interceptor this)
                    http/create-server
                    http/start))))
  (stop [this]
    (when pedestal-server
      (http/stop pedestal-server))
    (assoc this :pedestal-server nil)))

(defrecord TestPedestal [pedestal-config pedestal-servlet]
  component/Lifecycle
  (start [component]
    (assoc component :pedestal-servlet
                     (-> pedestal-config
                       (add-component-interceptor component)
                       http/create-servlet)))
  (stop [_]))

(defn- get-pedestal
  [context]
  (let [pedestal (get context ::pedestal-component ::not-found)]
    (when (nil? pedestal)
      (throw (ex-info (str "Pedestal component was nil in context map; "
                        "component.pedestal is not configured correctly")
               {:reason  ::nil-pedestal
                :context context})))
    (when (= ::not-found pedestal)
      (throw (ex-info (str "Pedestal component was missing from context map; "
                        "component.pedestal is not configured correctly")
               {:reason  ::missing-pedestal
                :context context})))
    pedestal))

(defn context-component
  "Returns the component at key from the Pedestal context map. key
  must have been a declared dependency of the Pedestal server component."
  [context key]
  (let [component (get (get-pedestal context) key ::not-found)]
    (when (nil? component)
      (throw (ex-info (str "Component " key " was nil in Pedestal dependencies; "
                        "maybe it returned nil from start or stop")
               {:reason         ::nil-component
                :dependency-key key
                :context        context})))
    (when (= ::not-found component)
      (throw (ex-info (str "Missing component " key " from Pedestal dependencies")
               {:reason         ::missing-dependency
                :dependency-key key
                :context        context})))
    component))

(defn component-handler
  "Returns a Pedestal interceptor which extracts the component named
  key from the context map. The key must have been declared a
  dependency of the Pedestal server component.

  Invokes f with two arguments, the component and the Ring-style
  request map. f should return a Ring-style response map.

  You can use this to replace Ring-style handler functions with
  functions that take both a component and a request."
  ([key f] (component-handler nil key f))
  ([name key f]
   (interceptor
     {:name  (interceptor-name name)
      :enter (fn [context]
               (let [c (context-component context key)]
                 (assoc context :response (f c (:request context)))))})))

(defn using-component
  "Returns an interceptor which associates the component named key
  into the Ring-style request map as :component. The key must have
  been declared a dependency of the Pedestal server component.

  You can add this interceptor to your Pedestal routes to make the
  component available to your Ring-style handler functions, which can
  get :component from the request map."
  [key]
  (interceptor
    {:name  ::using-component
     :enter (fn [context]
              (assoc-in context [:request ::component]
                (context-component context key)))}))

(defn use-component
  "Returns the component added to the request map by 'using-component'."
  [request]
  (::component request))

(defn pedestal
  "Returns a new instance of the Pedestal server component.

  pedestal-config-fn is a no-argument function which returns the
  Pedestal server configuration map, which will be passed to
  io.pedestal.http/create-server. If you want the default
  interceptors, you must call io.pedestal.http/default-interceptors
  in pedestal-config-fn.

  The Pedestal component should have dependencies (as by
  com.stuartsierra.component/using or system-using) on all components
  needed by your web application. These dependencies will be available
  in the Pedestal context map via 'context-component'.

  You can make components available to your handler functions with
  'using-component' or 'component-handler'."
  ([pedestal-config]
   (->Pedestal pedestal-config nil)))

(defn test-pedestal
  ([pedestal-config]
   (->TestPedestal pedestal-config nil)))