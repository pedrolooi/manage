package org.odk.manage.server.servlet;

import org.apache.commons.lang.StringEscapeUtils;
import org.odk.manage.server.AdminAccountsConfig;
import org.odk.manage.server.Constants;
import org.odk.manage.server.XmlUtils;
import org.odk.manage.server.model.DbAdapter;
import org.odk.manage.server.model.Device;
import org.odk.manage.server.model.Task.TaskStatus;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * The servlet serving the main admin page (/admin.html) for ODK Manage.
 * @author alerer@google.com (Your Name Here)
 *
 */
public class ManageAdminServlet extends ServletUtilBase {

  public static final String ADDR = "admin.html";

  private static final String[] devicePropertyNames = new String[]{
      "IMEI",
      "User ID",
      "Phone #",
      //"IMSI",
      //"SIM ID"
  };
  private static String devicePropertyThs = "";
  static {
    for (String name : devicePropertyNames) {
      devicePropertyThs = devicePropertyThs + "<th>" + name + "</th>";
    }
  }
  
  
  @Override
  public void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    
    // authenticate the user as an admin
    if (!AdminAccountsConfig.authenticateAdmin(req, resp)) {
      return;
    }
    
    DbAdapter dba = null;
    try {
      dba = new DbAdapter();
   
      List<Device> devices = dba.getDevices();
      
      // try to fetch a list of forms from the ODK Aggregate site
      List<OdkAggregateForm> aggregateForms = null;
      String aggregateDomain = dba.getPreference(Constants.AGGREGATE_DOMAIN_KEY);
      if (aggregateDomain != null) {
        aggregateForms = getFormsFromFormList(
            requestAggregateForms(aggregateDomain));
      }
  
      Writer out = resp.getWriter();
      out.write("<!DOCTYPE html PUBLIC '-//W3C//DTD XHTML 1.0 Transitional//EN'" +
              "'http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd'>\n");
      out.write("<html><head><title>ODK Manage Server</title>");
      out.write("<link href='main.css' type='text/css' rel='stylesheet'></link>");
      out.write("<script src='admin.js' type='text/javascript'></script>");
      out.write("</head>");
      out.write("<body onload='updateActionType();updateAggregateFormSelect()'><h1>ODK Manage Server</h1>"); 
      
      out.write("<div align='right' id='aggregateUrlDiv'><form action='admin.html' method='post'>");
      out.write("ODK Aggregate URL: <input type='text' name='" + Constants.AGGREGATE_DOMAIN_KEY + 
          "' value='" + StringEscapeUtils.escapeHtml(removeNull(aggregateDomain)) + "'/>");
      out.write("<input type='submit' value='Update URL' />");
      out.write("</form></div>");
      String actionMessage = req.getParameter("message");
      String actionMessageType = req.getParameter("messageType");
      if (actionMessage != null && actionMessageType != null){
        out.write("<div class='messageDiv' messageType='" + 
            StringEscapeUtils.escapeHtml(actionMessageType) + "'>" + 
            StringEscapeUtils.escapeHtml(actionMessage) + "</div>");
      }
      out.write("<div id='mainPanel'>");
      
      out.write("<div id='actionForm'>");
      out.write("<form action='" + DoActionServlet.ADDR + "' method='post'>");
      out.write("Perform Action: ");
      out.write("<select name='actionType' id='actionTypeSel' onchange='updateActionType()'>");
      out.write("<option value='' selected='true'></option>");
      out.write("<option value='ADD_FORM'>Task: Add Form</option>");
      out.write("<option value='INSTALL_PACKAGE'>Task: Install Package</option>");
      out.write("<option value='NEW_TASKS_SMS'>Send New Tasks Notification SMS</option>");
      out.write("<option value='SEND_SMS'>Send SMS Message</option>");
      // ----------------- add new actions here -------------------------------
      // out.write("<option value='[action name]'>[action display name]</option>");
      out.write("</select>");
      out.write("<br/>");
      
      out.write("<div class='actionInputs' id='ADD_FORM_INPUTS'>");
      if (aggregateForms != null){
        out.write("Choose a form: ");
        out.write("<select id='aggregateFormSelect' name='aggregateFormSelect' onchange='updateAggregateFormSelect()'>");
        for (OdkAggregateForm form : aggregateForms) {
          // I'm using the " symbol as a delimiter to separate the form name
          // from the form URL. Using this symbol prevents me from having to 
          // do additional escaping because HtmlEscape escapes the " symbol 
          // even though I'm using ' quotes.
          out.write("<option value='" + StringEscapeUtils.escapeHtml(form.name) + 
              "\"" + StringEscapeUtils.escapeHtml(form.url) + "'>" + 
              StringEscapeUtils.escapeHtml(form.name) + "</option>");
        }
        out.write("<option value='other'>Other...</option>");
        out.write("</select>");
        out.write("<div id='otherUrl'>" +
              "Form URL: <input name='ADD_FORM.url' type='text'></div>");

      } else {
        out.write("Form URL: <input type='text' name='ADD_FORM.url'/>");
      }
      out.write("</div>");
      
      out.write("<div class='actionInputs' id='INSTALL_PACKAGE_INPUTS'>");
      out.write("<table><tr><td>Package Name: </td><td><input type='text' name='INSTALL_PACKAGE.name' value='org.odk.collect.android'/></td>");
      out.write("<tr><td>Package URL: </td><td><input type='text' name='INSTALL_PACKAGE.url'/></td></table>");
      out.write("</div>");
      
      out.write("<div class='actionInputs'id='NEW_TASKS_SMS_INPUTS'>");
      out.write("Message: <br/><textarea name='NEW_TASKS_SMS.content' cols='35' rows='4'></textarea>");
      out.write("<br/>Note: Messages can be no longer than 120 characters.");
      out.write("</div>");
      
      out.write("<div class='actionInputs' id='SEND_SMS_INPUTS' >");
      out.write("Message: <br/><textarea name='SEND_SMS.content' cols='35' rows='4'></textarea>");
      out.write("<br/>Note: Messages can be no longer than 140 characters.");
      out.write("</div>");
      
      // ----------------- add new actions here (action inputs) -------------------
      // out.write("<div class='actionInputs' id='[action/task name]_INPUTS' >");
      // out.write("Input: <br/><textarea name='[action/task name].url' cols='35' rows='4'></textarea>");
      // out.write("</div>");
      
      out.write("<input id='actionSubmit' type='submit' value='Submit'/>");
      out.write("</div>");
      
      out.write("<table class='main'><tr>");
      out.write("<th><input type='checkbox' id='selectAllCheckbox' onclick='updateSelectAllDevices(this)'</th>");
      out.write(devicePropertyThs + "<th colspan=3>Tasks</th><th>View Tasks</th><th>SMS<th>Last Contacted</th></tr>");      
      
      Date now = new Date();
      for (Device device : devices) {
        int numPending = device.getTaskCount(TaskStatus.PENDING);
        int numSuccess = device.getTaskCount(TaskStatus.SUCCESS);
        int numFailed = device.getTaskCount(TaskStatus.FAILED);
        String status = (numFailed > 0) ? "FAILED" : (numPending > 0) ? "PENDING" : "";
        out.write("<tr class = 'device' status=" + status + ">");

        String[] deviceProperties = new String[]{ 
          device.getImei(), 
          device.getUserId(), 
          device.getPhoneNumber(), 
          //device.getImsi(), 
          //device.getSim() 
          };
        
        assert (devicePropertyNames.length == deviceProperties.length);
        
        // Checkbox TD
        out.write("<td><input type='checkbox' name='imei' value='" + device.getImei() + "' onclick='updateSelectedDevice()'/></td>");
        // Properties TD
        for (String property : deviceProperties) {
          out.write(getPropertyTd(property));
        }

        // Tasklist TD
        out.write("<td status=PENDING>" + (numPending>0?numPending:" ") + "</td>");
        out.write("<td status=SUCCESS>" + (numSuccess>0?numSuccess:" ") + "</td>");
        out.write("<td status=FAILED>" + (numFailed>0?numFailed:" ") + "</td>");
        out.write("<td>");
        out.write("<a href='" + ViewTasksServlet.ADDR + "?imei=" + device.getImei() + "'>View Tasks</a>");
        out.write("</td>");
        
        // Send notification SMS TD
        if (device.getSmsValidator()!=null) {
          out.write("<td status=SUCCESS>Yes</td>");
        } else {
          out.write("<td></td>");
        }
        
        //Last contacted TD
        out.write("<td>");
        if (device.getLastContacted() != null) {
          long ms = now.getTime() - device.getLastContacted().getTime();
          out.write(getDurationString(ms) + " ago.");
        }
        out.write("</td>");
        out.write("</tr>");
        
      }
      out.write("</table>");
      /**
       * Note that the action form INCLUDES the entire devices table - this way,
       * we know which devices are highlighted.
       */
      out.write("</form>");
      out.write("</div></body></html>");

    } finally {
      if (dba != null)
        dba.close();
    }
  }
  
  private String getPropertyTd(String property){
    return "<td class='property'>" + StringEscapeUtils.escapeHtml(removeNull(property)) + "</td>\n";
  }
  
  
  @Override
  /**
   * We're using POST on this servlet to update the ODK aggregate domain.
   */
  public void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    
    // authenticate the user as an admin
    if (!AdminAccountsConfig.authenticateAdmin(req, resp)) {
      return;
    }
    
    String aggregateDomain = req.getParameter(Constants.AGGREGATE_DOMAIN_KEY);
    if (aggregateDomain != null) {
      DbAdapter dba = null;
      try {
        dba = new DbAdapter();
        dba.setPreference(Constants.AGGREGATE_DOMAIN_KEY, aggregateDomain);
      } finally {
        if (dba != null)
          dba.close();
      }
    }
    doGet(req, resp);
  }
    

  // ---------------------------------------------------------
  // These methods handle the link to ODK Aggregate.
  
  private class OdkAggregateForm {
    public String name;
    public String url;
  }
  
  private InputStream requestAggregateForms(String aggregateDomain){
    if (aggregateDomain == null || aggregateDomain.equals(""))
      return null;
    if (aggregateDomain.charAt(aggregateDomain.length()-1) == '/')
      aggregateDomain = aggregateDomain.substring(0, aggregateDomain.length()-1);
    try {
      URL url = new URL(aggregateDomain + "/" + Constants.AGGREGATE_FORM_LIST_PATH);
      return url.openStream();
    } catch (MalformedURLException e) {
      logError("MalformedURLException", e);
      return null;
    } catch (IOException e) {
      logError("IOException", e);
      return null;
    }
  }
  
  private List<OdkAggregateForm> getFormsFromFormList(InputStream formList){
    if (formList == null) {
      return null;
    }
    Document doc = XmlUtils.getXmlDocument(formList);
    if (doc == null){
      return null;
    }
    
    List<OdkAggregateForm> forms = new ArrayList<OdkAggregateForm>();
    
    NodeList formNodes = doc.getElementsByTagName("form");

    for (int i = 0; i < formNodes.getLength(); i++) {
      if (!(formNodes.item(i) instanceof Element)) {
        continue;
      }
      
      Element formEl = (Element) formNodes.item(i);
      NamedNodeMap formAttributes = formEl.getAttributes();

      OdkAggregateForm form = new OdkAggregateForm();
      
      NodeList nl = formEl.getChildNodes();
      if (nl == null || nl.getLength() != 1 || nl.item(0).getNodeType() != Node.TEXT_NODE)
        continue;
      
      form.name = nl.item(0).getNodeValue();
      form.url = XmlUtils.getAttribute(formAttributes, "url");
      
      forms.add(form);
    }
    try {
      formList.close();
    } catch(IOException e) {}
    return forms;
  }
                
          
}


