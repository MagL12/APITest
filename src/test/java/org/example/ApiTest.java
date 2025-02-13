package org.example;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;
import org.apache.http.HttpStatus;
import org.example.config.Config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ApiTest {
    private static final String ITEM_ENDPOINT = "/api/1/item/{id}";
    private static final String EXISTING_ITEM_ID = "0cd4183f-a699-4486-83f8-b513dfde477a";
    private static final int EXPECTED_SELLER_ID = 1234345231;
    private static final String EXPECTED_ITEM_NAME = "dsdsd";
    private static final int EXPECTED_PRICE = 1;

    private static final Logger log = LoggerFactory.getLogger(ApiTest.class); // Инициализация логгера

    @BeforeAll
    public static void setup() {
        RestAssured.baseURI = Config.BASE_URL;
    }

    /**
     * TC-01: Проверка получения объявления по его идентификатору
     */
    @Test
    @DisplayName("TC-01")
    public void getItemById_WithValidId_ShouldReturnItemDetails() {
        // Given
        RequestSpecification request = given()
                .accept(ContentType.JSON)
                .pathParam("id", EXISTING_ITEM_ID);

        // When
        Response response = request.when().get(ITEM_ENDPOINT);

        // Then
        ValidatableResponse vr = response.then()
                .assertThat()
                .statusCode(HttpStatus.SC_OK)
                .body("", hasSize(1))
                .body("[0].id", equalTo(EXISTING_ITEM_ID))
                .body("[0].sellerId", equalTo(EXPECTED_SELLER_ID))
                .body("[0].name", equalTo(EXPECTED_ITEM_NAME))
                .body("[0].price", equalTo(EXPECTED_PRICE))
                .body("[0].statistics", allOf(
                        hasEntry("contacts", 3),
                        hasEntry("likes", 123),
                        hasEntry("viewCount", 12)
                ));



        // Soft assertions for additional validations
        JsonPath jsonPath = vr.extract().jsonPath();
        String createdAt = jsonPath.getString("[0].createdAt");

        assertAll("Detailed item validations",
                () -> assertThat(createdAt)
                        .matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d+ \\+\\d+ \\+\\d+"),
                () -> log.debug("Validated item: {}", jsonPath.getObject("[0]", Map.class))
        );

        log.info("Test TC-01 successfully passed!");

    }

    /**
     * TC-02: Проверка получения объявления по несуществующему идентификатору
     */
    @Test
    @DisplayName("TC-02")
    public void getItemById_WithInvalidId_ShouldReturnBadRequest() {
        // Given
        String invalidItemId = "nonexistent_id";
        String expectedErrorMessage = "передан некорректный идентификатор объявления";

        RequestSpecification request = given()
                .accept(ContentType.JSON)
                .pathParam("id", invalidItemId);

        // When
        Response response = request.when().get(ITEM_ENDPOINT);

        // Then
        response.then()
                .assertThat()
                .statusCode(HttpStatus.SC_BAD_REQUEST)
                .body(
                        "result.message", equalTo(expectedErrorMessage),
                        "result.messages", anEmptyMap(),
                        "status", equalTo("400")
                );

        // Логирование для отладки
        //  log.debug("Response for invalid ID {}: {}", invalidItemId, response.getBody().prettyPrint());
        log.info("Test TC-02 successfully passed!");
    }

    /**
     * TC-03: Проверка получения объявления по его идентификатору с внутренней ошибкой сервера
     */
    @Test
    @DisplayName("TC-03")
    public void getItemById_WithMalformedId_ShouldReturnBadRequest() {
        // Given
        String malformedItemId = "invalid_id_123%"; // Пример некорректного ID
        String expectedErrorMessage = "передан некорректный идентификатор объявления";

        RequestSpecification request = given()
                .accept(ContentType.JSON)
                .pathParam("id", malformedItemId);

        // When
        Response response = request.when().get(ITEM_ENDPOINT);

        // Then
        response.then()
                .assertThat()
                .statusCode(HttpStatus.SC_BAD_REQUEST)
                .body(
                        "result.message", equalTo(expectedErrorMessage),
                        "result.messages", anEmptyMap(),
                        "status", equalTo("400")
                );

        // Логирование для анализа
       // log.info("Server response for malformed ID:\n{}", response.getBody().prettyPrint());
        log.info("Test TC-03 successfully passed!");
    }

    /**
     * TC-04: Проверка создания объявления
     */
    @Test
    @DisplayName("TC-04")
    public void createItem_WithValidData_ShouldReturnSuccess() {
        // Given
        int sellerId = 123456;
        String itemName = "Test name";
        int itemPrice = 120;

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("sellerID", sellerId);
        requestBody.put("name", itemName);
        requestBody.put("price", itemPrice);

        RequestSpecification request = given()
                .contentType(ContentType.JSON)
                .accept(ContentType.JSON)
                .body(requestBody);

        // When
        Response response = request.when().post("/api/1/item");


        // Then
        ValidatableResponse vr = response.then()
                .assertThat()
                .statusCode(HttpStatus.SC_OK)
                .body("status", startsWith("Сохранили объявление -"));

        // Логирование созданного объявления
        String statusMessage = vr.extract().path("status");
       // log.info("Created item with status: {}", statusMessage);
        log.info("Test TC-04 successfully passed!");
    }

    /**
     * TC-05: Проверка создания объявления с пустым телом запроса
     */
    @Test
    @DisplayName("TC-05")
    public void createItem_WithEmptyRequestBody_ShouldReturnBadRequest() {
        // Given
        String sellerId = "<integer>";
        String itemName = "<string>";
        String itemPrice = "<integer>";

        Map<String, Object> requestBody = new HashMap<>();

        requestBody.put("sellerID", sellerId);
        requestBody.put("name", itemName);
        requestBody.put("price", itemPrice);


        RequestSpecification request = given()
                .contentType(ContentType.JSON) // Установка заголовка Content-Type
                .accept(ContentType.JSON)
                .body(requestBody); // Установка заголовка Accept

        // When: Отправка POST-запроса на создание объявления с пустым телом
        Response response = request.when().post("/api/1/item");

        // Then: Проверка результата
        response.then()
                .assertThat()
                .statusCode(HttpStatus.SC_BAD_REQUEST) // Проверяем статус-код 400 Bad Request
                .body("status", equalTo("не передан объект - объявление")) // Проверяем поле status
                .body("result.message", equalTo("")) // Проверяем поле result.message
                .body("result.messages", anEmptyMap()); // Проверяем, что result.messages является пустым объектом

        // Логирование фактического ответа
      //  log.info("Response Body: {}", response.getBody().prettyPrint());
        log.info("Test TC-05 successfully passed!");
    }

    /**
     * TC-09: Проверка получения объявления по несуществующему идентификатору
     */
    @Test
    @DisplayName("TC-09")
    public void getItemById_WithNonexistentId_ShouldReturnNotFound() {
        // Given: Подготовка запроса с несуществующим ID
        String nonexistentItemId = "0cd4183f-a699-4486-83f8-b513dfde477b";
        RequestSpecification request = given()
                .header("Accept", "application/json") // Установка заголовка Accept
                .pathParam("id", nonexistentItemId); // Передача параметра :id

        // When: Отправка GET-запроса на получение объявления по ID
        Response response = request.when().get("/api/1/item/{id}");

        // Then: Проверка результата
        ValidatableResponse vr = response.then()
                .assertThat()
                .statusCode(HttpStatus.SC_NOT_FOUND) // Проверяем статус-код 404 Not Found
                .body("status", equalTo("404")) // Проверяем поле status
                .body("result.message", equalTo("item " + nonexistentItemId + " not found")) // Проверяем сообщение об ошибке
                .body("result.messages", nullValue()); // Проверяем, что result.messages равно null

        // Логирование фактического ответа
      //  log.info("Response Body: {}", response.getBody().prettyPrint());
        log.info("Test TC-09 successfully passed!");
    }

    /**
     * TC-10: Проверка получения статистики по существующему объявлению
     */
    @Test
    @DisplayName("TC-10")
    public void getStatisticByItemId_WithValidId_ShouldReturnStatistic() {
        // Given: Подготовка запроса с существующим ID объявления
        String existingItemId = "0cd4183f-a699-4486-83f8-b513dfde477a"; // Идентификатор существующего объявления

        RequestSpecification request = given()
                .header("Accept", "application/json") // Установка заголовка Accept
                .pathParam("id", existingItemId); // Передача параметра :id

        // When: Отправка GET-запроса на получение статистики по ID
        Response response = request.when().get("/api/1/statistic/{id}");

        // Then: Проверка результата
        ValidatableResponse vr = response.then()
                .assertThat()
                .statusCode(HttpStatus.SC_OK) // Проверяем статус-код 200 OK
                .body("", hasSize(1)) // Проверяем, что массив содержит ровно один элемент
                .body("[0].contacts", equalTo(3)) // Проверяем поле contacts
                .body("[0].likes", equalTo(246)) // Проверяем поле likes
                .body("[0].viewCount", equalTo(258)); // Проверяем поле viewCount

        // Логирование фактического ответа
       // log.info("Response Body: {}", response.getBody().prettyPrint());
        log.info("Test TC-10 successfully passed!");
    }

    /**
     * TC-11: Проверка получения статистики по несуществующему объявлению
     */
    @Test
    @DisplayName("TC-11")
    public void getStatisticByItemId_WithNonexistentId_ShouldReturnNotFound() {
        // Given: Подготовка запроса с несуществующим ID объявления
        String nonexistentItemId = "0cd4183f-a699-4486-83f8-b513dfde477b";
        RequestSpecification request = given()
                .header("Accept", "application/json") // Установка заголовка Accept
                .pathParam("id", nonexistentItemId); // Передача параметра :id

        // When: Отправка GET-запроса на получение статистики по ID
        Response response = request.when().get("/api/1/statistic/{id}");

        // Then: Проверка результата
        ValidatableResponse vr = response.then()
                .assertThat()
                .statusCode(HttpStatus.SC_NOT_FOUND) // Проверяем статус-код 404 Not Found
                .body("status", equalTo("404")) // Проверяем поле status
                .body("result.message", equalTo("statistic " + nonexistentItemId + " not found")) // Проверяем сообщение об ошибке
                .body("result.messages", nullValue()); // Проверяем, что result.messages равно null

        // Логирование фактического ответа
       // log.info("Response Body: {}", response.getBody().prettyPrint());
        log.info("Test TC-11 successfully passed!");
    }

    /**
     * TC-12: Проверка получения статистики по объявлению с некорректным идентификатором
     */
    @Test
    @DisplayName("TC-12")
    public void getStatisticByInvalidId_ShouldReturnBadRequest() {
        // Given
        String invalidStatisticId = "invalid_id";
        String expectedErrorMessage = "передан некорректный идентификатор объявления";
        String statisticEndpoint = "/api/1/statistic/{id}";

        RequestSpecification request = given()
                .accept(ContentType.JSON)
                .pathParam("id", invalidStatisticId);

        // When
        Response response = request.when().get(statisticEndpoint);

        // Then
        response.then()
                .assertThat()
                .statusCode(HttpStatus.SC_BAD_REQUEST)
                .body(
                        "result.message", equalTo(expectedErrorMessage),
                        "result.messages", anEmptyMap(),
                        "status", equalTo("400")
                );

        // Логирование для анализа
        //log.info("Response for invalid statistic ID:\n{}", response.getBody().prettyPrint());
        log.info("Test TC-12 successfully passed!");
    }

    /**
     * TC-13: Проверка получения всех объявлений по идентификатору продавца
     */
    @Test
    @DisplayName("TC-13")
    public void getItemsBySellerId_WithValidSellerId_ShouldReturnItemsList() {
        // Given: Подготовка запроса с существующим sellerID
        String existingSellerId = "1234345231"; // Идентификатор существующего продавца

        RequestSpecification request = given()
                .header("Accept", "application/json") // Установка заголовка Accept
                .pathParam("sellerID", existingSellerId); // Передача параметра :sellerID

        // When: Отправка GET-запроса на получение всех объявлений по sellerID
        Response response = request.when().get("/api/1/{sellerID}/item");

        // Then: Проверка результата
        ValidatableResponse vr = response.then()
                .assertThat()
                .statusCode(HttpStatus.SC_OK) // Проверяем статус-код 200 OK
                .body("", not(empty())) // Проверяем, что массив не пустой
                .body("[0].sellerId", equalTo(Integer.parseInt(existingSellerId))) // Проверяем sellerId первого объявления
                .body("[0].statistics.contacts", greaterThanOrEqualTo(0)) // Проверяем contacts (неотрицательное значение)
                .body("[0].statistics.likes", greaterThanOrEqualTo(0)) // Проверяем likes (неотрицательное значение)
                .body("[0].statistics.viewCount", greaterThanOrEqualTo(0)); // Проверяем viewCount (неотрицательное значение)

        // Дополнительная проверка: логирование фактического ответа
        //log.info("Response Body: {}", response.getBody().prettyPrint());
        log.info("Test TC-13 successfully passed!");
    }

    /**
     *TC-14: Проверка получения всех объявлений для существующего продавца без объявлений
     */
    @Test
    @DisplayName("TC-14")
    public void getItemsBySellerId_WithExistingSellerButNoItems_ShouldReturnEmptyList() {
        // Given: Подготовка запроса с идентификатором существующего продавца без объявлений
        String sellerIdWithoutItems = "1234345230"; // Идентификатор существующего продавца без объявлений

        RequestSpecification request = given()
                .header("Accept", "application/json") // Установка заголовка Accept
                .pathParam("sellerID", sellerIdWithoutItems); // Передача параметра :sellerID

        // When: Отправка GET-запроса на получение всех объявлений по sellerID
        Response response = request.when().get("/api/1/{sellerID}/item");

        // Then: Проверка результата
        ValidatableResponse vr = response.then()
                .assertThat()
                .statusCode(HttpStatus.SC_OK) // Проверяем статус-код 200 OK
                .body("", equalTo(new ArrayList<>())); // Проверяем, что тело ответа — пустой массив []

        // Логирование фактического ответа
       // log.info("Response Body: {}", response.getBody().prettyPrint());
        log.info("Test TC-14 successfully passed!");
    }

    /**
     *TC-16: Проверка получения всех объявлений по некорректному идентификатору продавца
     */
    @Test
    @DisplayName("TC-16")
    public void getItemsByInvalidSellerId_ShouldReturnBadRequest() {
        // Given: Подготовка запроса с некорректным sellerID
        String invalidSellerId = "not_a_number"; // Некорректный идентификатор продавца

        RequestSpecification request = given()
                .header("Accept", "application/json") // Установка заголовка Accept
                .pathParam("sellerID", invalidSellerId); // Передача параметра :sellerID

        // When: Отправка GET-запроса на получение всех объявлений по sellerID
        Response response = request.when().get("/api/1/{sellerID}/item");

        // Then: Проверка результата
        ValidatableResponse vr = response.then()
                .assertThat()
                .statusCode(HttpStatus.SC_BAD_REQUEST) // Проверяем статус-код 400 Bad Request
                .body("status", equalTo("400")) // Проверяем поле status
                .body("result.message", equalTo("передан некорректный идентификатор продавца")) // Проверяем сообщение об ошибке
                .body("result.messages", anEmptyMap()); // Проверяем, что result.messages является пустым объектом {}

        // Логирование фактического ответа
       // log.info("Response Body: {}", response.getBody().prettyPrint());
        log.info("Test TC-16 successfully passed!");
    }

    /**
     *TC-22: Проверка уникальности идентификатора объявления
     */
    @Test
    @DisplayName("TC-22")
    public void createDuplicateItems_ShouldReturnUniqueIds() {
        // Given: Подготовка данных для создания объявления
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("sellerID", 123456);
        requestBody.put("name", "Duplicate Test");
        requestBody.put("price", 120);
        Map<String, Integer> statistics = new HashMap<>();
        statistics.put("contacts", 2);
        statistics.put("likes", 10);
        statistics.put("viewCount", 50);
        requestBody.put("statistics", statistics);

        RequestSpecification request = given()
                .header("Content-Type", "application/json") // Установка заголовка Content-Type
                .header("Accept", "application/json") // Установка заголовка Accept
                .body(requestBody); // Тело запроса

        // When: Отправка первого POST-запроса на создание объявления
        Response response1 = request.when().post("/api/1/item");

        // Then: Проверка результата для первого ответа
        String statusMessage1 = response1.then()
                .assertThat()
                .statusCode(HttpStatus.SC_OK) // Проверяем статус-код 200 OK
                .extract()
                .path("status"); // Извлекаем поле status

        // Когда: Отправка второго POST-запроса с теми же данными
        Response response2 = request.when().post("/api/1/item");

        // Тогда: Проверка результата для второго ответа
        String statusMessage2 = response2.then()
                .assertThat()
                .statusCode(HttpStatus.SC_OK) // Проверяем статус-код 200 OK
                .extract()
                .path("status"); // Извлекаем поле status

        // Проверка уникальности идентификаторов
        String id1 = extractItemIdFromStatus(statusMessage1); // Извлекаем ID из первого ответа
        String id2 = extractItemIdFromStatus(statusMessage2); // Извлекаем ID из второго ответа

        // Убеждаемся, что ID различаются
        assertThat(id1).isNotEqualTo(id2); // Проверяем, что ID не совпадают

        // Логирование фактических результатов
      //  log.info("First item ID: {}", id1);
       // log.info("Second item ID: {}", id2);
        log.info("Test TC-22 successfully passed!");
    }

    /**
     *TC-23: Проверка формата даты createdAt
     */
    @Test
    @DisplayName("TC-23")
    public void checkCreatedAtFormat() {
        // Given: Создание объявления через POST /api/1/item
        int sellerId = 123456;
        String name = "Test CreatedAt Format";
        int price = 100;

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("sellerID", sellerId);
        requestBody.put("name", name);
        requestBody.put("price", price);

        Response createResponse = given()
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .body(requestBody)
                .when()
                .post("/api/1/item");

        // Извлечение ID созданного объявления из статуса ответа
        String statusMessage = createResponse.then()
                .assertThat()
                .statusCode(200) // Убедимся, что создание прошло успешно
                .extract()
                .path("status");

        String itemId = extractItemIdFromStatus(statusMessage); // Метод для извлечения ID из строки status

        // When: Отправка GET-запроса на получение объявления по его ID
        Response getResponse = given()
                .header("Accept", "application/json")
                .pathParam("id", itemId)
                .when()
                .get("/api/1/item/{id}");

        // Then: Проверка формата поля createdAt
        String createdAt = getResponse.then()
                .assertThat()
                .statusCode(200) // Проверяем статус-код 200 OK
                .extract()
                .path("[0].createdAt"); // Извлекаем значение createdAt

        // Определяем регулярное выражение для проверки формата даты
        Pattern datePattern = Pattern.compile("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d+ \\+\\d{4} \\+\\d{4}");

        // Проверяем, что формат соответствует ожидаемому
        assertThat(createdAt).matches(datePattern);
        //assertThat("Field 'createdAt' has incorrect format", createdAt, matchesPattern(datePattern));

        // Логирование фактического значения createdAt
        log.info("Test TC-23 successfully passed!");
    }

    /**
     * Метод для извлечения идентификатора объявления из поля status.
     */
    private String extractItemIdFromStatus(String statusMessage) {
        if (statusMessage == null || !statusMessage.contains("-")) {
            fail("Invalid status message format: " + statusMessage);
        }
        return statusMessage.split(" - ")[1].trim(); // Возвращаем часть после "Сохранили объявление -"
    }
}
