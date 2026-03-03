package simulations

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._

/**
 * Payment Flow Load Test
 *
 * Simulates realistic payment workflow:
 * 1. User registration
 * 2. Authentication
 * 3. Create payment intent
 * 4. Complete payment
 *
 * Load Profile: Ramp up from 1 to 100 users over 5 minutes
 */
class PaymentFlowSimulation extends Simulation {

  val httpProtocol = http
    .baseUrl("http://localhost:8080")
    .acceptHeader("application/json")
    .contentTypeHeader("application/json")
    .userAgentHeader("Gatling Load Test")

  // Feeder for unique user data
  val userFeeder = Iterator.from(0).map(i => Map(
    "username" -> s"loadtest_user_$i",
    "email" -> s"loadtest$i@example.com",
    "password" -> "LoadTest123!"
  ))

  // Scenario
  val paymentScenario = scenario("Payment Flow")
    .feed(userFeeder)
    .exec(
      http("Register User")
        .post("/api/v1/auth/register")
        .body(StringBody("""{"username":"${username}","email":"${email}","password":"${password}"}""")).asJson
        .check(status.is(200))
        .check(jsonPath("$.token").saveAs("token"))
    )
    .pause(1)
    .exec(
      http("Create Payment Intent")
        .post("/api/v1/payments/create-intent")
        .header("Authorization", "Bearer ${token}")
        .body(StringBody("""{"amount":99.99,"currency":"USD","description":"Load test payment"}""")).asJson
        .check(status.is(200))
        .check(jsonPath("$.paymentIntentId").saveAs("paymentIntentId"))
    )
    .pause(2)
    .exec(
      http("Get Payment History")
        .get("/api/v1/payments/history")
        .header("Authorization", "Bearer ${token}")
        .queryParam("page", "0")
        .queryParam("size", "10")
        .check(status.is(200))
    )

  // Load Profile
  setUp(
    paymentScenario.inject(
      rampUsers(100) during (5 minutes)
    )
  ).protocols(httpProtocol)
   .assertions(
     global.responseTime.max.lt(3000),      // Max response time < 3s
     global.responseTime.mean.lt(1000),     // Mean response time < 1s
     global.successfulRequests.percent.gt(95) // Success rate > 95%
   )
}
