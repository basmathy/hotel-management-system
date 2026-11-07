package ru.basmathy.booking.configuration;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.*;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class HotelClientConfiguration {
  @Bean
  @LoadBalanced
  RestClient.Builder discoveredHotelClientBuilder() {
    return RestClient.builder();
  }

  @Bean
  public RestClient hotelClient(
      @Qualifier("discoveredHotelClientBuilder") RestClient.Builder discovery,
      @Value("${hotel.base-url}") String baseUrl,
      @Value("${hotel.internal-token}") String token,
      @Value("${hotel.connect-timeout:PT0.5S}") Duration connect,
      @Value("${hotel.read-timeout:PT1S}") Duration read) {
    HttpClient http = HttpClient.newBuilder().connectTimeout(connect).build();
    var factory = new JdkClientHttpRequestFactory(http);
    factory.setReadTimeout(read);
    var builder =
        "hotel-management-service".equals(URI.create(baseUrl).getHost())
            ? discovery
            : RestClient.builder();
    return builder
        .baseUrl(baseUrl)
        .requestFactory(factory)
        .defaultHeader("X-Internal-Token", token)
        .requestInterceptor(
            (request, body, execution) -> {
              String id = MDC.get("correlationId");
              if (id != null) request.getHeaders().set("X-Correlation-ID", id);
              return execution.execute(request, body);
            })
        .build();
  }
}
