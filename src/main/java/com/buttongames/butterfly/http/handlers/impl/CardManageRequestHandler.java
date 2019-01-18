package com.buttongames.butterfly.http.handlers.impl;

import com.buttongames.butterfly.hibernate.dao.impl.ButterflyUserDao;
import com.buttongames.butterfly.hibernate.dao.impl.CardDao;
import com.buttongames.butterfly.http.exception.InvalidRequestMethodException;
import com.buttongames.butterfly.http.handlers.BaseRequestHandler;
import com.buttongames.butterfly.model.ButterflyUser;
import com.buttongames.butterfly.model.Card;
import com.buttongames.butterfly.model.CardType;
import com.buttongames.butterfly.util.CardIdUtils;
import com.buttongames.butterfly.util.StringUtils;
import com.buttongames.butterfly.xml.XmlUtils;
import com.buttongames.butterfly.xml.builder.KXmlBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import spark.Request;
import spark.Response;

import java.time.LocalDateTime;

/**
 * Handler for any requests that come to the <code>cardmng</code> module.
 * @author skogaby (skogabyskogaby@gmail.com)
 */
@Component
public class CardManageRequestHandler extends BaseRequestHandler {

    private final Logger LOG = LogManager.getLogger(CardManageRequestHandler.class);

    /**
     * The DAO for managing cards in the database.
     */
    private final CardDao cardDao;

    /**
     * The DAO for managing users in the database.
     */
    private final ButterflyUserDao userDao;

    public CardManageRequestHandler(final CardDao cardDao, final ButterflyUserDao userDao) {
        this.cardDao = cardDao;
        this.userDao = userDao;
    }

    /**
     * Handles an incoming request for the <code>cardmng</code> module.
     * @param requestBody The XML document of the incoming request.
     * @param request The Spark request
     * @param response The Spark response
     * @return A response object for Spark
     */
    @Override
    public Object handleRequest(final Element requestBody, final Request request, final Response response) {
        final String requestMethod = request.attribute("method");

        if (requestMethod.equals("inquire")) {
            return this.handleInquireRequest(requestBody, request, response);
        } else if (requestMethod.equals("getrefid")) {
            return this.handleGetRefIdRequest(requestBody, request, response);
        } else {
            throw new InvalidRequestMethodException();
        }
    }

    /**
     * Handles an incoming request for the <code>cardmng.inquire</code> module.
     * @param requestBody The XML document of the incoming request.
     * @param request The Spark request
     * @param response The Spark response
     * @return A response object for Spark
     */
    private Object handleInquireRequest(final Element requestBody, final Request request, final Response response) {
        final Node requestNode = XmlUtils.nodeAtPath(requestBody, "/cardmng");
        final String cardId = requestNode.getAttributes().getNamedItem("cardid").getNodeValue();
        final int cardTypeInt = Integer.parseInt(requestNode.getAttributes().getNamedItem("cardtype").getNodeValue());

        // see if the card is bound already
        Card card = this.cardDao.findByNfcId(cardId);

        // if it is, return the data, otherwise return a status response
        KXmlBuilder builder = KXmlBuilder.create("response");

        if (card == null) {
            builder = builder.e("cardmng").a("status", "112");
        } else {
            builder = builder.e("cardmng")
                                    .a("binded", "1")
                                    .a("dataid", card.getRefId())
                                    .a("ecflag", "1") // TODO: See if this is related to heat level or something
                                    .a("expired", "0")
                                    .a("newflag", "0")
                                    .a("refid", card.getRefId());
        }

        return this.sendResponse(request, response, builder);
    }

    /**
     * Handles an incoming request for the <code>cardmng.getrefid</code> module.
     * @param requestBody The XML document of the incoming request.
     * @param request The Spark request
     * @param response The Spark response
     * @return A response object for Spark
     */
    private Object handleGetRefIdRequest(final Element requestBody, final Request request, final Response response) {
        // this request is to create a new card
        final Node requestNode = XmlUtils.nodeAtPath(requestBody, "/cardmng");
        final String cardId = requestNode.getAttributes().getNamedItem("cardid").getNodeValue();
        final int cardTypeInt = Integer.parseInt(requestNode.getAttributes().getNamedItem("cardtype").getNodeValue());
        final CardType cardType = CardType.values()[cardTypeInt];
        final String pin = requestNode.getAttributes().getNamedItem("passwd").getNodeValue();

        // create a new user that this card is bound to
        final ButterflyUser newUser = new ButterflyUser(pin, LocalDateTime.now(), LocalDateTime.now(), 10000);
        userDao.create(newUser);

        // create the card and save it
        final Card card = new Card(newUser, cardType, cardId, CardIdUtils.encodeCardId(cardId),
                StringUtils.getRandomHexString(16), LocalDateTime.now(), LocalDateTime.now());
        this.cardDao.create(card);

        // send a response
        final KXmlBuilder builder = KXmlBuilder.create("response")
                .e("cardmng").a("dataid", card.getRefId()).a("refid", card.getRefId());
        return this.sendResponse(request, response, builder);
    }
}
