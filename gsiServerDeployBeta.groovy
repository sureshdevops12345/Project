import com.gsihealth.jenkins.runner.DeployTask
import com.gsihealth.jenkins.runner.DeploymentRunner
import com.gsihealth.jenkins.utils.CommonUtils
import com.gsihealth.jenkins.utils.Constants
import com.gsihealth.jenkins.utils.DeploymentSummary
import com.gsihealth.jenkins.utils.EmailListBuilder
import com.gsihealth.jenkins.utils.Logger

def call(body) {


    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    node {

        Logger logger = new Logger()

        logger.info('Starting deployment task...')

        deleteDir()

        DeploymentRunner deploymentRunner = new DeploymentRunner(steps)
        DeployTask deployTask = deploymentRunner.initializeTask(env.JOB_NAME, config, binding.variables)

        logger.info deployTask

        if( deploymentRunner.isGlassfishDeployment(deployTask) ){
            deploymentRunner.restartGlassfish(deployTask)
        }

        stage('Deploy') {
            deploymentRunner.deploy(deployTask)

            if(deployTask.debug){ currentBuild.result = "FAILURE"}
            deployTask.result = currentBuild.result
        }

        stage('PostJob') {

            logger.info ("Archiving metadata of deployment...")
            archive "${Constants.OUT_DIR}/*"

            DeploymentSummary deploymentSummary = new DeploymentSummary(steps)
            deploymentSummary.populateData(deployTask, env, currentBuild)

            String[] recipients =
                    new EmailListBuilder(steps)
                            .CDTeam()
                            .requestedUser()
                            .build()

            logger.info("Sending to ${recipients.join(',')}")

            deploymentSummary.publishReport()

            boolean isSuccess = currentBuild.result == null || currentBuild.result == 'SUCCESS'

            if(deployTask.shouldSendEmail()){
                deploymentSummary.sendEmail(recipients, !isSuccess)
            }

        }

    }
}


