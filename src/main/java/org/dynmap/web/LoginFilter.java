package org.dynmap.web;

import org.dynmap.DynmapCore;
import org.dynmap.Log;
import org.eclipse.jetty.http.HttpStatus.Code;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import java.io.IOException;
import java.util.HashSet;

public class LoginFilter implements Filter {
    private DynmapCore core;
    public static final String USERID_GUEST = "_guest_";
    public static final String USERID_ATTRIB = "userid";
    public static final String LOGIN_PAGE = "/login.html";
    public static final String LOGIN_POST = "/up/login";
    
    
    public LoginFilter(DynmapCore core) {
        this.core = core;
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException { }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest)request;
        HttpServletResponse resp = (HttpServletResponse)response;

        /* Get session - initialize if needed */
        HttpSession sess = req.getSession(true);
        String uid = (String)sess.getAttribute(USERID_ATTRIB);
        if(uid == null) {
            uid = USERID_GUEST;
            sess.setAttribute(USERID_ATTRIB, uid);   /* Set to guest access */
        }
        if(sess.isNew()) {
            sess.setMaxInactiveInterval(60);    /* Intialize to 60 seconds */
        }
        String uri = req.getRequestURI();
        if(uri.startsWith("/login/")) { /* Allow login.html to be loaded always */
        }
        else if(uri.equals("/up/login")) {  /* Process login form */
            uid = req.getParameter("j_username");
            String pwd = req.getParameter("j_password");
            if((uid == null) || (uid.equals("")))
                uid = USERID_GUEST;
            if(core.checkLogin(uid, pwd)) {
                sess.setAttribute(USERID_ATTRIB, uid);
                resp.sendRedirect("/index.html");
            }
            else {
                resp.sendRedirect("/login/login.html?failed=true");
            }
            return;
        }
        else if(uri.equals("/up/register")) {  /* Process register form */
            uid = req.getParameter("j_username");
            String pwd = req.getParameter("j_password");
            String passcode = req.getParameter("j_passcode");
            if(core.registerLogin(uid, pwd, passcode)) {    /* Good registration? */
                sess.setAttribute(USERID_ATTRIB, uid);
                resp.sendRedirect("/index.html");
            }
            else {
                resp.sendRedirect("/login/register.html?failed=true");
            }
            return;
        }
        else {
            if(core.getLoginRequired() && uid.equals(USERID_GUEST)) {
                resp.sendRedirect("/login/login.html");
                return;
            }
        }
        
        chain.doFilter(request, response);
    }

    @Override
    public void destroy() { }
}
