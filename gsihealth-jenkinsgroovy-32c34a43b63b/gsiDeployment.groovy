import jenkins.model.Jenkins
import com.gsihealth.jenkins.Common
import groovy.time.*

def call(body) {

    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    def envName = config.env
    def appName = config.app
    def warName = config.war
    def release = config.release
    def branch = config.branch?:release
    def force = config.force?:false

    //Flag to mark the build result status
    def failure = false
	def duration = null
    node {

        try {
			def timeStart = new Date()
            stage 'Deploy'

            // Marks build start
                echo "[BUILD START]"

            //Get workspace path of current job
                def workspace = pwd()
            //The name of the build job to copy the artifact from
                def buildProjectName = "Build-${branch}-${appName}"
            // Command to invoke deployment script
                def PS_deploy = 'powershell.exe -command "D:/jenkins_utils/delete_remote_app_folder.ps1 ' +
                        "${envName} ${appName} ${warName} ${workspace} ${release}" +
                        '";exit $LASTEXITCODE;'
            //Copy artifact
                step([$class: 'CopyArtifact', filter: '**/*.war, **/*.zip', fingerprintArtifacts: true, flatten: true, projectName: buildProjectName, selector: [$class: 'StatusBuildSelector', stable: false]])
            //Update current build name and description with SVN revision number
                if(!setSvnRev(buildProjectName,release,force)) {
                    echo "[INFO] The latest build is already deployed. Skipping this deployment..."
                    //Marks build end
	                echo "[BUILD END]"
                } else {
		            //Execute deployment
		                bat "${PS_deploy}"
		
		            stage 'Test'
		
		                echo "Starting test job..."
		                echo "Test is currently disabled. skipping..."
		//            TODO: parametrize this to call appropriate post-deployment jobs
		            // Run test job
		//                build 'Post-DeployToDev-Tests'
		
		            stage 'Archive'
		
		            //Archive everything in the current job workspace
		                archive '**/*'
		            //Log parser
		                step([$class: 'LogParserPublisher', parsingRulesPath: 'D:/jenkins_utils/gf-deployment-parser', useProjectRule: false])
		
		            //Marks build end
		                echo "[BUILD END]"
		        }

				def timeStop = new Date()
				duration=getTimeDelta(timeStop,timeStart)
        }catch(e){

            //Marks build end
                echo "[BUILD END]"

                print e
                failure=true

        }

        //If any errors mark build as unsuccessful.
        if(failure){
            currentBuild.result = "FAILURE"
        }

        try{

            //Email list
            def requested = emailextrecipients([ [$class: 'RequesterRecipientProvider']])
            def culprits = emailextrecipients([[$class: 'CulpritsRecipientProvider']])
            def developers = emailextrecipients([ [$class: 'DevelopersRecipientProvider']])
            def common = new Common();
            def cdteam = common.getCD().join(", ")
            def devteam = common.getDevLead().join(", ")

            println "Started by: ${requested}"
            println "Culprits: ${culprits}"
            println "Developers: ${developers}"


            def to = cdteam+", ${requested}"
            print "to: ${to}"


            // Retrieve console log
            def jobPath = "${env.JENKINS_HOME}"
            jobPath = getJobPath(jobPath, env.JOB_NAME)
            def consolePath = jobPath+ "\\builds\\${env.BUILD_NUMBER}\\log"

            sleep 5
            echo "reading the console log..."
            def log = readFile(consolePath)
            def buildLog =  lastMatch(log, /\[BUILD START\]([\s|\S]*)\[BUILD END\]/)
            if(buildLog){
                buildLog = replaceAll(buildLog,/\[8mh.+\[Pipeline\](?:(?!\r\n)[\s|\S])*\r\n/,"")
                buildLog = replaceAll(buildLog,/\[8mh.+\[Pipeline\]/,"")
            }

            //Generate email content
            def IP_URL = replaceUrl(env.BUILD_URL, "10.128.65.100:8989")
            def BUILD_STATUS = currentBuild.result?:"SUCCESS"
            def DEFAULT_SUBJECT = "${env.JOB_NAME} - ${currentBuild.displayName} - ${BUILD_STATUS}!"
            def DEFAULT_CONTENT = "${env.JOB_NAME} - ${currentBuild.displayName} - ${BUILD_STATUS}:<br><br>"

            if(duration!=null) {
            	DEFAULT_CONTENT +="This deployment took by ${duration}.<br />"
            }
            if(requested){
                DEFAULT_CONTENT +="This deployment was started by ${requested}.<br />"
            }
            if(developers){
                DEFAULT_CONTENT +="Developers related to this deployment: ${developers}.<br />"
            }
            if(culprits){
                DEFAULT_CONTENT +="Developers responsible for this deployment: ${culprits}.<br />"
            }
            DEFAULT_CONTENT += """
            Check the result page at <a href="${env.BUILD_URL}">${env.BUILD_URL}</a> or <a href="${IP_URL}">${IP_URL}</a>.<br><br>
            """.stripMargin()

            if(buildLog && buildLog.length()!=0){
                DEFAULT_CONTENT+="<br>=========CONSOLE LOG=========<br><br><pre>${buildLog}</pre>"
            }

            //Send email
            if(to!=null && to.length()!=0){
                emailext body: DEFAULT_CONTENT, subject: DEFAULT_SUBJECT, to: to
            }

        }catch(e){
            print e
            emailext body: "Script has some error! ${e}", subject: "Jenkins warning:${env.JOB_NAME} Build ${currentBuild.displayName}", to: 'sadha.sivim@gsihealth.com'

        }

    }

}


@NonCPS
private boolean setSvnRev(buildProjectName, release,force) {
    def currJob = Jenkins.instance.getItem("${env.JOB_NAME}")
    def lastSuccess = currJob.getLastSuccessfulBuild()
    def revisionToCompare = 0
    if(lastSuccess != null)
    	revisionToCompare = lastMatch(lastSuccess.getDisplayName(), /Rev(\d+)/)

    def item = Jenkins.instance.getItem(buildProjectName)
    def ff = item.getLastSuccessfulBuild()
    def SVN_REVISION = lastMatch(ff.getDisplayName(), /Rev(\d+)/)

    if (SVN_REVISION) {
        currentBuild.displayName = "#${env.BUILD_NUMBER}_${release}.Rev${SVN_REVISION}"
    }
    if(!force&&(!SVN_REVISION||SVN_REVISION == revisionToCompare)){
        return false;
    }
    return true;
}

@NonCPS
def getJobPath(rootPath, job_name) {
    def arr = job_name.split("/")
    for (int i = 0; i < arr.length; i++) {
        rootPath += "\\jobs\\${arr[i]}"
    }
    return rootPath
}
//(Checking out[\s|\S]*)stage \(Archive\)
@NonCPS
def String lastMatch(text, regex) {
    def m;
    if ((m = text =~ regex)) {
        return m[0][-1]
    }
    return null;
}

@NonCPS
def String replaceUrl( text, text2) {
    return text.replaceAll("Jenkins01:8989", text2)
}
@NonCPS
def String replaceAll( text, regex, newText) {
    return text.replaceAll(regex, newText)
}

@NonCPS
def getTimeDelta(timeStop, timeStart){
    return TimeCategory.minus(timeStop,timeStart).toString()
}