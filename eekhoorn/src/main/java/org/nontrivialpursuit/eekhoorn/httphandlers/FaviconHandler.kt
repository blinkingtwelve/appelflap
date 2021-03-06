package org.nontrivialpursuit.eekhoorn.httphandlers

import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.handler.ContextHandler
import org.eclipse.jetty.util.B64Code
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class FaviconHandler(contextPath: String) : ContextHandler() {

    init {
        allowNullPathInfo = true
        setContextPath(contextPath)
    }

    override fun doHandle(
            target: String, baseRequest: Request, request: HttpServletRequest, response: HttpServletResponse) {
        if (target != "/") return
        response.contentType = "image/x-icon"
        response.setContentLengthLong(favicon.size.toLong())
        response.outputStream.use {
            it.write(favicon)
        }
        baseRequest.isHandled = true
    }

    companion object {
        val favicon: ByteArray = B64Code.decode(
            "AAABAAEAEBAAAAAAAABoBQAAFgAAACgAAAAQAAAAIAAAAAEACAAAAAAAAAEAAAAAAAAAAAAAAAEAAAAAAAAFImcAAAAAAMDM5QAAKX0ABR9fAD9lsQAgQYsAADCTACRFiAACLYsANFOSAOjo6AAXRaIADi99AAE0mQAlTqEAMFmrAAAwlAAIJ3MA9fX1AObm5gALLXsAM1abAF18uwDf5fIAEj6WABpIowApTpoA4uLiAK++3gAAMpgABC2FAP7+/gA1UpcAUHK4AAgxggAGJG0AM1ytAKCy2AAeS6QA3t7eAAUoewAvSYgA8PL4AA84kAAVQp0AEz+VAEBmsgAmTJkAaIbCAAArhACQptIAWHGjABA/ngAeSJ0AQleKANra2gDg5vIABCp/AAYndwAWRKEAv8vlAAAwkAALKncABSZyANbW1gAeR5kAL1irABlHogATQqAAEjOAAMPDwwDP2OsALE2RACBMpQAJx4YAo67GAEeVIAAGJnQABDaaACJDjQADKoIA7OzsAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAQEBSQceHgM7OztORgEBAQEBAQkeHh46Ozs7O0YBAQEBARcOHh4eHh4eCkcLAQEBKyxORTVPHh4fJAAEBE0BATM6ThknPE8eEjs7O0tLSwEvTk4jGictFU4/KkwBSwEBNTs6CSMuDU5AN0E4FAEBAS86OhEJUDs7QAgwARsTAQEYEU4eHg8hBlEeHjweNFIBASJRHh4eHh4JHh5EHhkoAQE9PhEeHh4eAQEeRB4BOAEBAUoJHh4OHiABHkQeIDgBAQEmOh4eQxoyHh4MHhYUAQEBKykeQwVICQNCHkIJAQEBORA+SiUmAR42HDgcEQEBGAUQDDEdAQFKSAEBAUoCAeAHAADgBwAAwAcAAAADAAAAAQAAAAsAAAAHAAAAAwAAAAEAAIABAACAAQAAwAEAAMABAADAAwAAgQMAAAM5AAA="
        )
    }
}