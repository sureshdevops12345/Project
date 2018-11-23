import jenkins.model.Jenkins
import hudson.scm.SubversionChangeLogSet.LogEntry
import com.gsihealth.jenkins.Common
def call(body) {

    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
	def Patientid = config.Patientid
    body()
node{
    File file = new File("C:\\KatalonDataFiles\\DemoPerfection\\Data Files\\SeverityAlerts.txt")
    File existingXMLFile = new File("D:\\Sadha\\soapProjectFiles\\Alerts-soapui-project.xml")
    String textexistingXMLFile=existingXMLFile.text
    String dataFromFile=file.text
    String[] descriptiondatas = dataFromFile.split("\\n")
    for(String descriptionWithPatientID : descriptiondatas){
    String[] splitDescriptionWithPatientID=descriptionWithPatientID.split("&&")
    String endPointURL = splitDescriptionWithPatientID[0].trim()
    String alertDescription = splitDescriptionWithPatientID[1].trim()
    String patientID = splitDescriptionWithPatientID[2].trim()
    String xmlFile=textexistingXMLFile.replaceAll("<<endPointURL>>",endPointURL)
    xmlFile=xmlFile.replaceAll("<<alertDescription>>",alertDescription)
    xmlFile=xmlFile.replaceAll("<<patientID>>",patientID)
    println xmlFile
    File filenew = new File("D:\\Sadha\\soapProjectFiles\\newlyUpdatedfile.xml")
    filenew << xmlFile
    ProcessBuilder builder = new ProcessBuilder(
    "cmd.exe", "/c", "SmartBear\\SoapUI-5.4.0\\bin\\testrunner.bat -sAutomate -c\"BHIX Alert trigger\" \"D:\\Sadha\\soapProjectFiles\\newlyUpdatedfile.xml\" ");
    filenew.delete()
     }
    }
	}