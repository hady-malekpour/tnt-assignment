# TNT FEDEX assignment
# How to run
If you have docker installed and, you do not need to be super user to run docker command simply run the following command
```bash
./run.sh
```
Then visit this url: http://127.0.0.1:8080/swagger-ui.html

If you do not have docker installed, run the following commands in the provided order:
```bash
./mvnw clean install
java -jar target/target/assignment-0.0.1-SNAPSHOT.jar
```
In this case it is your own responsibility to provide external services.

# How to stop
If you run the application with docker then simply run following command:
 ```bash
 ./stop.sh
 ```

# Extra information
The solution implemented fully reactive, spring webflux has been used, please visit
[web reactive](https://docs.spring.io/spring/docs/current/spring-framework-reference/web-reactive.html "web reactive")


The rest api has been documented by [openapi](https://github.com/springdoc/springdoc-openapi "openapi").


There were three stories, the first one was straight forward but to understand the implemented solution for second and third, we have to know the concept of processor:

* [What is processor in project reactor](https://ducmanhphan.github.io/2019-08-25-How-to-use-Processor-in-Reactor-Java/) 
* [What is UnicastProcessor](https://projectreactor.io/docs/core/release/api/reactor/core/publisher/UnicastProcessor.html)
* [How to subscribed an stream by multiple subscribers and send each event to all](https://www.reactiveprogramming.be/project-reactor-flux-sharing/)  

I have also added some inline comments in the code to make it easier to read and undrestand the solution.

Thanks for your time

....



I am also sharing with you the feedback from FEDEX on this assignment which I have recieved a month after submiting my code!

In my view when automated test is running to assess the assignment, it would make more sense to provide the failure test senarios instead of a vague descriptive statement!


################## HERE IS THHE FEEDBACK FROM FEDEX TEAM #######################

* Solution is not production ready, it cannot handle concurrent requests. I would not recommend him for future processing. Automated test result: 36 tests completed, 3 failed Concurrent aggregation features:
 * Respond to a medium volume of requests FAILED, expected:<100> but was:<11>
 * Respond to a high volume of requests FAILED, expected:<1000> but was:<0>
 * Validate service still works after any high-volume failures - does not works

* I don't approve this candidate. His reactive code is much more complex than it should, also some of our load tests hang forever.
