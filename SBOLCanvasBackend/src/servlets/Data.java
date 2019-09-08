package servlets;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpStatus;

import com.google.gson.Gson;

import utils.SBOLData;

@SuppressWarnings("serial")
@WebServlet(urlPatterns = {"/data/types"})
public class Data extends HttpServlet {
	
		protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
			// setup the json
			Gson gson = new Gson();
			String body = gson.toJson(SBOLData.getTypes());
			
			// write it to the response body
			ServletOutputStream outputStream = response.getOutputStream();
			InputStream inputStream = new ByteArrayInputStream(body.getBytes());
			IOUtils.copy(inputStream, outputStream);
			
			// the request was good
			response.setStatus(HttpStatus.SC_OK);
			response.addHeader("Access-Control-Allow-Origin", "*");
			response.setContentType("application/json");
			return;
		}
	
}
