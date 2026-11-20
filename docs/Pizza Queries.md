# Eample Queries for the Pizza Model
https://www.youtube.com/@wouterbeek


```SPARQL
# Example 1: List all Named Pizzas

PREFIX pizza: <http://www.co-ode.org/ontologies/pizza/pizza.owl#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>

SELECT ?pizza
WHERE {
  ?pizza rdfs:subClassOf pizza:NamedPizza .
}
```

```SPARQL
# Example 2: Query pizzas by a specific ingredient
PREFIX pizza: <http://www.co-ode.org/ontologies/pizza/pizza.owl#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
PREFIX owl: <http://w3.org>

SELECT ?pizza
WHERE {
  ?pizza rdfs:subClassOf [ 
    a owl:Restriction ;
    owl:onProperty pizza:hasTopping ;
    owl:someValuesFrom ?topping 
  ] .
  ?topping rdfs:subClassOf pizza:VegetableTopping .
}
```


```SPARQL
# Example 3: Finding Hot/Spicy Pizzas
PREFIX pizza: <http://www.co-ode.org/ontologies/pizza/pizza.owl#>
PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
PREFIX owl: <http://w3.org>

SELECT ?hotPizza
WHERE {
  ?hotPizza rdfs:subClassOf ?restriction .
  ?restriction owl:onProperty pizza:hasSpiciness ;
               owl:someValuesFrom pizza:Hot .
}
```