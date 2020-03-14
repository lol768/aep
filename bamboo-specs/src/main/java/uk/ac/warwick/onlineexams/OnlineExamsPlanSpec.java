package uk.ac.warwick.onlineexams;

import com.atlassian.bamboo.specs.api.BambooSpec;
import com.atlassian.bamboo.specs.api.builders.deployment.Deployment;
import com.atlassian.bamboo.specs.api.builders.plan.Job;
import com.atlassian.bamboo.specs.api.builders.plan.Plan;
import com.atlassian.bamboo.specs.api.builders.plan.Stage;
import com.atlassian.bamboo.specs.api.builders.plan.artifact.Artifact;
import com.atlassian.bamboo.specs.api.builders.project.Project;
import com.atlassian.bamboo.specs.api.builders.requirement.Requirement;
import com.atlassian.bamboo.specs.builders.task.*;
import com.atlassian.bamboo.specs.model.task.ScriptTaskProperties;
import uk.ac.warwick.bamboo.specs.AbstractWarwickBuildSpec;

import java.util.Collection;
import java.util.Collections;

/**
 * Plan configuration for Bamboo.
 * Learn more on: <a href="https://confluence.atlassian.com/display/BAMBOO/Bamboo+Specs">https://confluence.atlassian.com/display/BAMBOO/Bamboo+Specs</a>
 */
@BambooSpec
public class OnlineExamsPlanSpec extends AbstractWarwickBuildSpec {

  private static final Project PROJECT =
    new Project()
      .key("EXAMS")
      .name("Exam Management");

  private static final String LINKED_REPOSITORY = "Online Exams";

  private static final String SLACK_CHANNEL = "#project-paperwait";

  public static void main(String[] args) {
    new OnlineExamsPlanSpec().publish();
  }

  @Override
  protected Collection<Plan> builds() {
    return Collections.singleton(
      build(PROJECT, "ONLINE", "Online Exams")
        .linkedRepository(LINKED_REPOSITORY)
        .description("Build application")
        .stage(
          new Stage("Build stage")
            .jobs(
              new Job("Build and check", "BUILD")
                .tasks(
                  new VcsCheckoutTask()
                    .description("Checkout source from default repository")
                    .checkoutItems(new CheckoutItem().defaultRepository()),
                  new NpmTask()
                    .description("dependencies")
                    .nodeExecutable("Node 10")
                    .command("ci"),
                  new ScriptTask()
                    .description("Run tests and package")
                    .interpreter(ScriptTaskProperties.Interpreter.BINSH_OR_CMDEXE)
                    .location(ScriptTaskProperties.Location.FILE)
                    .fileFromPath("sbt")
                    .argument("clean integration/clean test:compile test integration/test universal:packageZipTarball")
                    .environmentVariables("PATH=/usr/nodejs/10/bin"),
                  new NpmTask()
                    .description("JS Tests")
                    .nodeExecutable("Node 10")
                    .command("run bamboo")
                )
                .finalTasks(
                  TestParserTask.createJUnitParserTask()
                    .description("Parse test results")
                    .resultDirectories("**/test-reports/*.xml"),
                  TestParserTask.createMochaParserTask()
                    .defaultResultDirectory()
                )
                .artifacts(
                  new Artifact()
                    .name("tar.gz")
                    .copyPattern("app.tar.gz")
                    .location("target/universal")
                    .shared(true),
                  new Artifact()
                    .name("Integration")
                    .copyPattern("**")
                    .location("it/target/test-html")
                    .shared(true),
                  new Artifact()
                    .name("Integration screenshots")
                    .copyPattern("**")
                    .location("it/target/screenshots")
                    .shared(true)
                )
                .requirements(
                  new Requirement("Linux").matchValue("true").matchType(Requirement.MatchType.EQUALS)
                )
            )
        )
        .slackNotifications(SLACK_CHANNEL, false)
        .build()
    );
  }

  @Override
  protected Collection<Deployment> deployments() {
    return Collections.emptyList();
    // TODO OE-3 Requires setup of infra
//    return Collections.singleton(
//      deployment(PROJECT, "ONLINE", "Online Exams")
//        .autoPlayEnvironment("Development", "onlineexams-dev.warwick.ac.uk", "onlineexams", "dev", SLACK_CHANNEL)
//        .autoPlayEnvironment("Test", "onlineexams-test.warwick.ac.uk", "onlineexams", "test", SLACK_CHANNEL)
//        .autoPlayEnvironment("Sandbox", "onlineexams-sandbox.warwick.ac.uk", "onlineexams", "sandbox", SLACK_CHANNEL, "master")
//        .playEnvironment("Production", "onlineexams.warwick.ac.uk", "onlineexams", "prod",
//          env -> env.notifications(
//            new Notification()
//              .type(new DeploymentStartedAndFinishedNotification())
//              .recipients(slackRecipient(SLACK_CHANNEL))
//          )
//        )
//        .build()
//    );
  }

}
