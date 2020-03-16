package helpers;

import com.google.common.io.ByteSource;
import uk.ac.warwick.util.virusscan.VirusScanResult;
import uk.ac.warwick.util.virusscan.VirusScanService;
import uk.ac.warwick.util.virusscan.VirusScanServiceStatus;
import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

class ThisIsFineVirusScanService implements VirusScanService {


  public ThisIsFineVirusScanService() {
    // nuffin
  }

  @PostConstruct
  public void init() {
    // nuffin
  }

  @Override
  public CompletableFuture<VirusScanResult> scan(ByteSource in) throws IOException {
    CompletableFuture<VirusScanResult> result = new CompletableFuture<>();
    result.complete(new VirusScanResult() {
      @Override
      public Status getStatus() {
        return Status.clean;
      }

      @Override
      public Optional<String> getVirus() {
        return Optional.empty();
      }

      @Override
      public Optional<String> getError() {
        return Optional.empty();
      }
    });
    return result;
  }

  @Override
  public CompletableFuture<VirusScanServiceStatus> status() {
    CompletableFuture<VirusScanServiceStatus> result = new CompletableFuture<>();
    result.complete(new VirusScanServiceStatus() {
      @Override
      public boolean isAvailable() {
        return true;
      }

      @Override
      public String getStatusMessage() {
        return "This is fine";
      }
    });
    return result;
  }
}
