import hudson.model.ParameterValue
import hudson.model.ParametersAction
import jenkins.model.Jenkins
import hudson.scm.SubversionChangeLogSet.LogEntry
import groovy.json.JsonOutput
import groovy.json.JsonSlurperClassic
import com.gsihealth.jenkins.Common
import groovy.time.*
import groovy.text.GStringTemplateEngine


def call(body) {

    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()

    env.OUT_DIR = 'out'
    env.LAST_SUC_DIR = 'lastSuccess'

    node {

        def timeStart = new Date()
        deleteDir()

        def environment = config.environment
        def server = config.server
        def branch = config.branch
        def release = config.release
        def spring = config.spring?:false
        def nodeApp = config.nodeApp?:false
        def custom = config.custom?:[:]

        def force = getProperty("Force deploy").toBoolean()
        def sleepBeforeDeploy = getBinding().hasVariable("Sleep before deploy")?getProperty("Sleep before deploy").toInteger():0
        def disableEmail = getBinding().hasVariable("Disable email")?getProperty("Disable email").toBoolean():false

        def deployApps = getDeployApps()
        // list of apps to be deployed
        def curSrcMap = [:]
        def reqDeployApps = []

        def info = [:]
        info.server = server
        info.requestedApps = deployApps
        info.environment = environment
        info.branch = branch
        info.release = release
        info.force = force
        info.start = timeStart

        step([
                projectName         : "${env.JOB_NAME}",
                $class              : 'CopyArtifact',
                fingerprintArtifacts: true,
                optional            : true,
                target              : "./${env.LAST_SUC_DIR}/",
                selector            : [$class: 'StatusBuildSelector', stable: false
                ]])

        def lastSrcMap = null
        def srcInfoFilePath = "${env.LAST_SUC_DIR}/${env.OUT_DIR}/source_info.json"

        if (fileExists(srcInfoFilePath)) {
            print "Reading previous metadata..."
            def prev = readFile srcInfoFilePath
            lastSrcMap = readFromJson(prev)
        }

        for (int i = 0; i < deployApps.size(); i++) {

            def appName = deployApps[i]

            def lastSrc = getLastSrc(appName, lastSrcMap)
            if (lastSrc.number != 0) print "[${appName}] Last deployed build: ${lastSrc.jobName} - #${lastSrc.number}(${lastSrc.buildName})"
            def _branch = branch
            if(custom[appName] && custom[appName].branch && custom[appName].branch!=branch){
                _branch = custom[appName].branch
                print "[${appName}] Using custom branch '${_branch}' for ${appName}..."
            }
            def _srcProject = custom[appName]&&custom[appName].srcProject ? custom[appName].srcProject : appName
            print "Build-${_branch}-${_srcProject}"
            def curSrc = getCurSrc("Build-${_branch}-${_srcProject}", lastSrc.number)

            def srcBuildNum = curSrc.srcBuild.number

            if ( force || lastSrc.jobName!= curSrc.name || lastSrc.number < srcBuildNum) {
                if (force) {
                    print "[${appName}] 'force' option is enabled."
                } else {
                    print "[${appName}] New build found: ${curSrc.name} #${srcBuildNum}"
                }
                reqDeployApps.add(appName)
            } else {
                print "[${appName}] No new build found from ${lastSrc.jobName}. Skipping ${appName} deployment..."
            }

            //push to global list
            curSrcMap[appName] = curSrc

        }

        info.deployedApps=reqDeployApps

        def _proceed = reqDeployApps.size() != 0
        def quotedApps = ""
        if (!_proceed) {
            print "All applications are latest builds. If you want to force deployment, use 'force' option."
        } else {
            print "Apps to be deployed: ${reqDeployApps}"
            quotedApps = getQuotedListString(reqDeployApps)
        }

        if(!spring&&!nodeApp){

            stage('Restart') {
                if (_proceed) {
                    def PS_restart = "D:/jenkins_utils/deployment_v2/ps/Restart-GFServer.ps1 " +
                            "-env '${environment}' -server '${server}' -appList @(${quotedApps})"
                    bat script: "${getPSCmd(PS_restart)}"
                }
            }
        }

        catchError {
            stage('Deploy') {

                if (_proceed) {
                    def buildName = (new Date()).format("yyyy_MM_dd__HH_mm_ss")
                    def workspace = pwd()
                    if(sleepBeforeDeploy > 0){
                        sleep sleepBeforeDeploy
                    }
                    print "Copying artifacts..."
                    for (def i = 0; i < reqDeployApps.size(); i++) {

                        def appName = reqDeployApps[i]
                        def userInput = true
                        def didTimeout = false
                        try {
                            timeout(time: 10, unit: 'SECONDS') {
                                userInput = input(
                                        id: 'Proceed1',
                                        message: "Starting ${appName} deployment in 10 seconds.")
                            }
                            print userInput
                        } catch (err) {
                            def user = err.getCauses()[0].getUser()
                            if ('SYSTEM' == user.toString()) { // SYSTEM means timeout.
                                didTimeout = true
                            } else {
                                userInput = false
                            }
                            if (!userInput) error message: "Aborted by: [${user}]"
                        }

                        def srcBuildNum = curSrcMap[appName].srcBuild.number
                        def srcProject = curSrcMap[appName].name
                        step([
                                projectName         : srcProject,
                                $class              : 'CopyArtifact',
                                filter              : '**/*.war, **/*.zip, **/*.jar',
                                fingerprintArtifacts: true,
                                flatten             : true,
                                selector            : [$class: 'SpecificBuildSelector', buildNumber: "${srcBuildNum}"]
                        ])
                        def _release = release
                        if(custom[appName] && custom[appName].release && custom[appName].release!=release){
                            _release = custom[appName].release
                            print "Using different release '${release}' for ${appName}..."
                        }

                        def PS_deploy = "D:/jenkins_utils/deployment_v2/ps/"
                        PS_deploy += spring ? "Deploy-SpringModulesBackup.ps1 " : nodeApp ? "Deploy-NodeModulesBackup.ps1 " : "Deploy-Modules.ps1 "
                        PS_deploy += "-env '${environment}' -server '${server}' -appList @('${appName}') -sourceDir ${workspace} -release '${_release}' -buildName '${buildName}'"
                        bat script: "${getPSCmd(PS_deploy)}"

                        //merge previous and current source info
                        def updatedSrcMap = updateSrcMapForApp(appName, curSrcMap[appName], lastSrcMap)
                        // save
                        writeFileAsJson("${workspace}/${env.OUT_DIR}/source_info.json", updatedSrcMap)

                    }

                }

            }
        }

        stage('PostJob') {

            def workspace = pwd()
            print "Archiving metadata of deployment..."
            archive "${env.OUT_DIR}/*"

            info.stop = new Date()
            info.requestedUser = emailextrecipients([[$class: 'RequesterRecipientProvider']])
            info.duration = TimeCategory.minus(info.stop, info.start).toString()
            def common = new Common()
            def cdteam = common.getCD().join(", ")
            def defaultRecipients = cdteam

            def summary = [
                    build  : getCurrentBuildData(info),
                    modules: getModulesData(info, curSrcMap)
            ]
            writeFileAsJson("${workspace}/report/summary.json", summary)

            writeFile file: "./report/index.html", text: createReport(summary)
            publishHTML(target: [allowMissing: true, keepAll: true, reportDir: 'report', reportFiles: 'index.html', reportName: 'Deployment Report'])

            if(!disableEmail){
                def email = createEmail(summary, defaultRecipients)
                //Send email
                def attachLog = true
                if (currentBuild.result == null || currentBuild.result == "SUCCESS") attachLog = false
                if (email.to != null && email.to.size() != 0) {
                    emailext body: email.content, subject: email.subject, to: email.to, mimeType: "text/html", attachLog: attachLog
                }
            }

        }

    }
}


@NonCPS
def getCurSrc(srcProjectName, prevSrcNum=0){

    def info = [
            name: srcProjectName,
            srcBuild:[:],
            changeSetList:[]
    ]

    def project = Jenkins.instance.getItem(srcProjectName)
    def lastSuc=project.getLastSuccessfulBuild()
    def lastSucNum = lastSuc.getNumber()
    info.srcBuild.number = lastSucNum
    info.srcBuild.name = lastSuc.getDisplayName()

    def buildChangeSets = getChangeSetsFromBuild(lastSuc)
    if(buildChangeSets) info.changeSetList.add(buildChangeSets)
    if(prevSrcNum > 0
            && (lastSucNum-1) > prevSrcNum){
        for(def i=lastSucNum-1; i>prevSrcNum; i--){
            def prevBuild = project.getBuildByNumber(i)
            def prevBuildChangeSets = getChangeSetsFromBuild(prevBuild)
            info.changeSetList.add(prevBuildChangeSets)
        }
    }
    return info
}

@NonCPS
def getChangeSetsFromBuild(build){

    if(build == null) {
        return null
    }

    def changeSets = build.getChangeSets()
    if(changeSets.size()==0){
        return null
    }
    def info = [
            number:build.getNumber(),
            name:build.getDisplayName(),
            changeSets:[]
    ]
    for(def j=0; j<changeSets.size(); j++){
        def changeSetLogs = (List<LogEntry>) changeSets.get(j).getLogs()
        for(def k=0; k<changeSetLogs.size(); k++){
            def logEntry = changeSetLogs.get(k)
            def changeSet = [
                    author:logEntry.getAuthor().toString(),
                    revision:logEntry.getRevision(),
                    message:logEntry.getMsg(),
                    date: logEntry.getDate(),
                    paths : []
            ]

            for(def p=0; p<logEntry.getPaths().size(); p++){
			try{
                changeSet.paths.add(logEntry.getPaths().get(p).getValue())
				}catch(Exception e){
changeSet.paths.add(null)
 print e
 }
}
            info.changeSets.add(changeSet)
        }
    }
    return info
}

@NonCPS
def String getPSCmd(command){
    def prefix = "powershell.exe -command \""
    def suffix = "\";exit \$LASTEXITCODE;"
    return prefix+command+suffix
}

@NonCPS
def List<String> getDeployApps(){
    def deployApps = []
    def myBuildParams = currentBuild.rawBuild.getAction(ParametersAction.class)
    for(ParameterValue p in myBuildParams) {
        if(p.name.startsWith("Deploy")){
            if(p.value==true){
                deployApps.add(p.name.split(" ")[1])
            }
        }
    }
    return deployApps
}

@NonCPS
def writeFileAsJson(filename, data){
    def jsonOutput = new JsonOutput()
    writeFile file:filename, text:jsonOutput.toJson(data)

}


@NonCPS
def readFromJson(data){
    def jsonSlurper = new JsonSlurperClassic()
    return jsonSlurper.parseText(data)
}

@NonCPS
def String getQuotedListString(list){
    def quoted = []
    for(int i = 0; i < list.size(); i++){
        quoted.add("'${list[i]}'")
    }
    return quoted.join(",")
}


@NonCPS
def updateSrcMap(curSrcMap, lastSrcMap){
    if(!lastSrcMap) lastSrcMap = [:]
    for ( e in curSrcMap ) {
        if(!lastSrcMap[e.key]) lastSrcMap[e.key]=[:]
        lastSrcMap[e.key].name = e.value.name
        lastSrcMap[e.key].srcBuild = e.value.srcBuild
    }
    return lastSrcMap
}

@NonCPS
def updateSrcMapForApp(appName, appMap, lastSrcMap){
    if(!lastSrcMap) lastSrcMap = [:]

    if(!lastSrcMap[appName]) lastSrcMap[appName]=[:]
    lastSrcMap[appName].name = appMap.name
    lastSrcMap[appName].srcBuild = appMap.srcBuild

    return lastSrcMap
}


@NonCPS
def getLastSrc(appName, lastSrcMap){
    def lastSrc = [ number:0,jobName:null,buildName:null ]
    if(lastSrcMap && lastSrcMap[appName]){
        lastSrc.jobName = lastSrcMap[appName].name
        lastSrc.buildName = lastSrcMap[appName].srcBuild.name
        lastSrc.number = lastSrcMap[appName].srcBuild.number
    }
    return lastSrc

}

@NonCPS
def String replaceAll( text, regex, newText) {
    return text.replaceAll(regex, newText)
}

@NonCPS
def createReport(summary){
    def engine = new GStringTemplateEngine()
    def template = new File("${env.JENKINS_HOME}/email-templates/deployment-report.groovy")
    return engine.createTemplate(template.getText()).make(summary).toString()
}

@NonCPS
def createEmail(summary, defaultRecipients){
    def email = [:]
    email.to=defaultRecipients
    email.subject = "[${summary.build.status}] ${summary.build.jobName} - ${summary.build.buildName}"
    def engine = new GStringTemplateEngine()
    def template = new File("${env.JENKINS_HOME}/email-templates/deployment-html.groovy")
    email.content = engine.createTemplate(template.getText()).make(summary).toString()
    return email
}

def getModulesData(info, curSrcMap) {

    def modulesData = []

    for (def i = 0; i < info.requestedApps.size(); i++) {
        def data = [:]
        def appName = info.requestedApps.get(i)
        data.appName = appName
        data.srcJob = curSrcMap[appName]
        if (appName in info.deployedApps) {
            def dataPath = "./report/data/${appName}.json"
            if (fileExists(dataPath)) {
                def jsonData = readFile dataPath
                data += readFromJson(jsonData)
            } else {
                data.status = "not-run"
            }
        } else {
            data.status = "no-change"
        }
        modulesData.add(data)
    }
    return modulesData
}

def getCurrentBuildData(info) {
    def ipUrl = replaceAll(env.BUILD_URL, "Jenkins01", "10.128.65.100")
    def homeIpUrl = replaceAll(env.JENKINS_URL, "Jenkins01", "10.128.65.100")
    def buildData = [
            homeUrl  : "${env.JENKINS_URL}",
            homeIpUrl : "${homeIpUrl}",
            url      : "${env.BUILD_URL}",
            reportUrl: "${env.BUILD_URL}Deployment_Report/",
            ipUrl    : "${ipUrl}",
            ipReportUrl    : "${ipUrl}Deployment_Report/",
            jobName  : "${env.JOB_NAME}",
            buildName: "${currentBuild.displayName}",
            status : currentBuild.result?:"SUCCESS"
    ]
    buildData = info + buildData
    buildData
}
