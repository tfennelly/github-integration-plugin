package org.jenkinsci.plugins.github.pullrequest.trigger.check;

import com.google.common.base.Predicate;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.CompareToBuilder;
import org.jenkinsci.plugins.github.pullrequest.GitHubPRPullRequest;
import org.jenkinsci.plugins.github.pullrequest.GitHubPRRepository;
import org.jenkinsci.plugins.github.pullrequest.utils.LoggingTaskListenerWrapper;
import org.kohsuke.github.GHPullRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckForNull;
import java.io.IOException;

/**
 * @author lanwen (Merkushev Kirill)
 */
public class NotUpdatedPRFilter implements Predicate<GHPullRequest> {
    private static final Logger LOGGER = LoggerFactory.getLogger(NotUpdatedPRFilter.class);

    private final GitHubPRRepository localRepo;
    private final LoggingTaskListenerWrapper logger;

    private NotUpdatedPRFilter(GitHubPRRepository localRepo, LoggingTaskListenerWrapper logger) {
        this.localRepo = localRepo;
        this.logger = logger;
    }

    public static NotUpdatedPRFilter notUpdated(GitHubPRRepository localRepo, LoggingTaskListenerWrapper logger) {
        return new NotUpdatedPRFilter(localRepo, logger);
    }

    @Override
    public boolean apply(GHPullRequest remotePR) {
        @CheckForNull GitHubPRPullRequest localPR = localRepo.getPulls().get(remotePR.getNumber());

        if (!isUpdated(remotePR, localPR)) { // light check
            logger.debug("PR [#{} {}] not changed", remotePR.getNumber(), remotePR.getTitle());
            return false;
        }
        return true;
    }

    /**
     * lightweight check that comments and time were changed
     */
    private static boolean isUpdated(GHPullRequest remotePR, GitHubPRPullRequest localPR) {
        if (localPR == null) {
            return true; // we don't know yet
        }
        try {

            boolean prUpd = new CompareToBuilder()
                    .append(localPR.getPrUpdatedAt(), remotePR.getUpdatedAt()).build() < 0; // by time

            boolean issueUpd = new CompareToBuilder()
                    .append(localPR.getIssueUpdatedAt(), remotePR.getIssueUpdatedAt()).build() < 0;

            boolean headUpd = !StringUtils.equals(localPR.getHeadSha(), remotePR.getHead().getSha()); // or head?
            boolean updated = prUpd || issueUpd || headUpd;

            if (updated) {
                LOGGER.info("Pull request #{} was updated at: {} by {}",
                        localPR.getNumber(), localPR.getPrUpdatedAt(), localPR.getUserLogin());
            }

            return updated;
        } catch (IOException e) {
            // should never happen because 
            LOGGER.warn("Can't compare PR [#{} {}] with local copy for update",
                    remotePR.getNumber(), remotePR.getTitle(), e);
            return false;
        }
    }
}

