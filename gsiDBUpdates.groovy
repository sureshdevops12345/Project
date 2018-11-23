import com.gsihealth.jenkins.Common
def call(body) {

// format of call from jenkins job:
//	gsiDBUpdates{
//		svnBranch="trunk"
//		profile="dev"
//	}

    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()
    def failure = false
    def repo_url = "https://subversion.assembla.com/svn/gsihealth.umbrella/${config.svnBranch}/dbChanges"

    node {

        try{
            //Marks build start
            echo "[BUILD START]"

	        stage 'Liquibase config'

	        stage 'DDL/DML checkout'

            //Checkout the latest code
            checkout([
                    $class              : 'SubversionSCM',
                    filterChangelog     : false,
                    ignoreDirPropChanges: false,
                    locations           : [[credentialsId        : '01385bf2-a087-4913-8972-ef0611c04367',
                                            depthOption          : 'infinity',
                                            ignoreExternalsOption: true,
                                            local                : "dbChanges",
                                            remote               : repo_url]],
                    workspaceUpdater    : [$class: 'CheckoutUpdater']])

			stage 'Build'

			dir("./dbChanges") {
				String goal = "--settings settings.xml liquibase:clearCheckSums liquibase:update -Dliquibase.changeLogFile=${config.schema}-changeLog.xml -P${config.profile}"
	            withEnv(["PATH+MAVEN=${tool name: 'Maven 3.0.4', type: 'hudson.tasks.Maven$MavenInstallation'}/bin",
	                     "MAVEN_OPTS=-XX:MaxPermSize=512m"]) {
	                bat "mvn ${goal} -Dmaven.repo.local=D:\\.m2\\repository"
	            }
	        }

            //Marks build end
            echo "[BUILD END]"

        }catch(e){
            print e
            echo "[BUILD END]"
            failure = true
        }

        if (failure) currentBuild.result = "FAILURE"

        try{

            def jobPath = "${env.JENKINS_HOME}"
            jobPath = getJobPath(jobPath, env.JOB_NAME)
            def consolePath = jobPath+ "\\builds\\${env.BUILD_NUMBER}\\log"

            def revision_path = jobPath+ "\\builds\\${env.BUILD_NUMBER}\\revision.txt"
            echo "reading the svn revision log..."
            def rev_log = readFile(revision_path)

            def BUILD_STATUS = currentBuild.result?:"SUCCESS"
            def SVN_REVISION = lastMatch(rev_log, /\/(\d+)$/)

            if(SVN_REVISION){
                currentBuild.displayName = "#${env.BUILD_NUMBER}_${config.svnBranch}.Rev${SVN_REVISION}"
                currentBuild.description = "Revision: ${SVN_REVISION}"
            }

            sleep 5
            echo "reading the console log..."
            def log = readFile(consolePath)

            def to;
            def requested = emailextrecipients([ [$class: 'RequesterRecipientProvider']])
            def culprits = emailextrecipients([[$class: 'CulpritsRecipientProvider']])
            def developers = emailextrecipients([ [$class: 'DevelopersRecipientProvider']])
            def common = new Common();
            def dbteam = common.getDBTeam.join(", ");
            println "Started by: ${requested}"
            println "Responsible Developers: ${culprits}"
            println "Developers: ${developers}"

            to = "${dbteam},"+ emailextrecipients([ [$class: 'RequesterRecipientProvider'],[$class: 'DevelopersRecipientProvider'],[$class: 'CulpritsRecipientProvider']])
            to = replaceAll(to,/, /,",")
            to = replaceAll(to,/ /,",")
            to = "sadha.sivim@gsihealth.com"

            print "to: ${to}"
            def buildLog =  lastMatch(log, /\[BUILD START\]([\s|\S]*)\[BUILD END\]/)
            if(buildLog){
                buildLog=replaceAll(buildLog,/\[8mh.+\[Pipeline\](?:(?!\r\n)[\s|\S])*\r\n/,"")
                buildLog=replaceAll(buildLog,/\[8mh.+\[Pipeline\]/,"")
            }

            def IP_URL = replaceUrl(env.BUILD_URL, "10.128.65.100:8989")

            def DEFAULT_SUBJECT = "[${BUILD_STATUS}] - ${env.JOB_NAME} - ${currentBuild.displayName}"
            def headlineColor = BUILD_STATUS=="SUCCESS"?"#26c6da":"#B71C1C"
            def DEFAULT_CONTENT = "<span style='color:${headlineColor};'>${env.JOB_NAME} - ${currentBuild.displayName} - ${BUILD_STATUS}</span> <br><br>"
            if(requested){
                DEFAULT_CONTENT +="This build was started by ${requested}.<br />"
            }
            if(developers){
                DEFAULT_CONTENT +="Developers related to this build: ${developers}.<br />"
            }
            if(culprits){
                DEFAULT_CONTENT +="Developers responsible for this build: ${culprits}.<br />"
            }
            DEFAULT_CONTENT += """<br>
            This build is for SVN Revision ${SVN_REVISION}<br><br>
            Check the build page at <a href="${env.BUILD_URL}">${env.BUILD_URL}</a> or <a href="${IP_URL}">${IP_URL}</a>.<br><br>
            """.stripMargin()
            if(buildLog && buildLog.length()!=0){
                DEFAULT_CONTENT+="<br>=========CONSOLE LOG=========<br><br><pre>${buildLog}</pre>"
            }
            if(to!=null && to.length()!=0){
                emailext body: DEFAULT_CONTENT, subject: DEFAULT_SUBJECT, to: to

            }
        }catch(e){
             emailext body: "Script has some error! ${e}", subject: "Jenkins warning:${env.JOB_NAME} Build ${currentBuild.displayName}", to: 'sadha.sivim@gsihealth.com'
            
        }

    }


}s


@NonCPS
def getJobPath(rootPath, job_name) {
    def arr = job_name.split("/")
    for (int i = 0; i < arr.length; i++) {
        rootPath += "\\jobs\\${arr[i]}"
    }
    return rootPath
}
//(Checking out[\s|\S]*)stage \(Archive\)
//Don't forget capture group...!
@NonCPS
def String lastMatch(text, regex) {
    def m;
    if ((m = text =~ regex)) {
        return m[0][-1]
    }
    return ''
}

@NonCPS
def String replaceUrl( text, text2) {
    return text.replaceAll("Jenkins01:8989", text2)
}
@NonCPS
def String replaceAll( text, regex, newText) {
    return text.replaceAll(regex, newText)
}