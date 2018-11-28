def call(body) {

    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
	def Repo = config.Repo

    body()
node{
    stage('postman tests'){
     echo "[BUILD START]"
	 	 print "${Repo}"
	 	 print "${config.Repo}"
        git "https://sadhasivim@bitbucket.org/GSIHTeam/${config.Repo}.git" 
          if("${config.Repo}"=="postmantest") {	
        try{
            dir("./test"){
                
        //bat "newman run AAAMicroservices_InternalIP.postman_collection.json -e ${config.environment1}.postman_environment.json --reporters cli,junit --reporter-junit-export newman.xml"
        bat "newman run AAAMicroservices_InternalIP.postman_collection.json -e ${config.environment1}state.postman_environment.json --reporters cli,junit --reporter-junit-export newman.xml"
		
		currentBuild.result="SUCCESS"
        status = true
            }
        }
        catch(Exception ex){
          currentBuild.result="FAILURE"
          status = false
        }
        step([$class: 'JUnitResultArchiver', testResults: '**/newman.xml'])
        
         try{
            dir("./test"){
       //bat "newman run AAAMicroservices_InternalIP.postman_collection.json -e ${config.environment2}.postman_environment.json --reporters cli,junit --reporter-junit-export newman.xml"
         bat "newman run AAAMicroservices_InternalIP.postman_collection.json -e ${config.environment2}state.postman_environment.json --reporters cli,junit --reporter-junit-export newman.xml"
		currentBuild.result="SUCCESS"
            }
        }
        catch(Exception ex){
            currentBuild.result="FAILURE"
			status = false
        }
        step([$class: 'JUnitResultArchiver', testResults: '**/newman.xml'])
         echo "[BUILD END]"
         }else{
		  try{
            dir("./${config.folderName}"){
       bat "newman run ${config.collectionJson} -e ${config.Json} --reporters cli,junit --reporter-junit-export newman.xml"
		currentBuild.result="SUCCESS"
         }   
        }
        catch(Exception ex){
            currentBuild.result="FAILURE"
			status = false
        }
        step([$class: 'JUnitResultArchiver', testResults: '**/newman.xml'])
         echo "[BUILD END]"
		 
		 }
       
        def jobPath = "${env.JENKINS_HOME}"
            jobPath = getJobPath(jobPath, env.JOB_NAME)
            def consolePath = jobPath+ "\\builds\\${env.BUILD_NUMBER}\\log"
			sleep 5
            echo "reading the console log..."
            def log = readFile(consolePath)
			def buildLog =  lastMatch(log, /\[BUILD START\]([\s|\S]*)\[BUILD END\]/)
            if(buildLog){
                buildLog=replaceAll(buildLog,/\[8mh.+\[Pipeline\](?:(?!\r\n)[\s|\S])*\r\n/,"")
                buildLog=replaceAll(buildLog,/\[8mh.+\[Pipeline\]/,"")			
				}
				def DEFAULT_CONTENT
		   if(buildLog && buildLog.length()!=0){
                DEFAULT_CONTENT="<br>=========CONSOLE LOG=========<br><br><pre>${buildLog}</pre>"
            }else{
			DEFAULT_CONTENT="liquibase execution failed for ${env.JOB_NAME}"
			} 
if("${config.Repo}"=="postmantest") {				
			if (currentBuild.result == "FAILURE"){	   
		   emailext body: "${DEFAULT_CONTENT}", mimeType: 'text/html', subject: "${env.JOB_NAME} build ${currentBuild.result}", to: 'Dinesh.Netaji@gsihealth.com,sadha.sivim@gsihealth.com,Murugesan.Nambi@gsihealth.com'
        }else{
        emailext body: "${DEFAULT_CONTENT}", mimeType: 'text/html', subject: "${env.JOB_NAME} build ${currentBuild.result}", to: 'Dinesh.Netaji@gsihealth.com,sadha.sivim@gsihealth.com,Murugesan.Nambi@gsihealth.com'
        }
        }else{
		if (currentBuild.result == "FAILURE"){	   
		   emailext body: "${DEFAULT_CONTENT}", mimeType: 'text/html', subject: "${env.JOB_NAME} build ${currentBuild.result}", to: 'dayanidhi.kasi@gsihealth.com,hemanth.kumar@gsihealth.com,sadha.sivim@gsihealth.com,Murugesan.Nambi@gsihealth.com'
        }else{
        emailext body: "${DEFAULT_CONTENT}", mimeType: 'text/html', subject: "${env.JOB_NAME} build ${currentBuild.result}", to: 'dayanidhi.kasi@gsihealth.com,hemanth.kumar@gsihealth.com,sadha.sivim@gsihealth.com,Murugesan.Nambi@gsihealth.com'
        }
		}
    }
 }   
    
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

