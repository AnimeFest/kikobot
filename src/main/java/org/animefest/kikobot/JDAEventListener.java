package org.animefest.kikobot;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import net.dv8tion.jda.core.entities.ChannelType;
import net.dv8tion.jda.core.entities.Guild;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.events.Event;
import net.dv8tion.jda.core.events.ReadyEvent;
import net.dv8tion.jda.core.events.StatusChangeEvent;
import net.dv8tion.jda.core.events.guild.GenericGuildEvent;
import net.dv8tion.jda.core.events.guild.voice.GenericGuildVoiceEvent;
import net.dv8tion.jda.core.events.http.HttpRequestEvent;
import net.dv8tion.jda.core.events.message.GenericMessageEvent;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import net.dv8tion.jda.core.events.user.UserTypingEvent;
import net.dv8tion.jda.core.events.user.update.UserUpdateGameEvent;
import net.dv8tion.jda.core.events.user.update.UserUpdateOnlineStatusEvent;
import net.dv8tion.jda.core.hooks.EventListener;
import opennlp.tools.chunker.ChunkerME;
import opennlp.tools.chunker.ChunkerModel;
import opennlp.tools.lemmatizer.DictionaryLemmatizer;
import opennlp.tools.postag.POSModel;
import opennlp.tools.postag.POSTaggerME;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import opennlp.tools.tokenize.TokenizerME;
import opennlp.tools.tokenize.TokenizerModel;

public class JDAEventListener implements EventListener {

    private TokenizerME tokenizer;
    private POSTaggerME posTagger;
    private DictionaryLemmatizer lemmatizer;
    private ChunkerME chunker;

    public JDAEventListener() throws IOException {
        InputStream inputStream = KikoBot.class.getResourceAsStream("/models/en-token.bin");
        TokenizerModel tokenizerModel = new TokenizerModel(inputStream);
        tokenizer = new TokenizerME(tokenizerModel);

        InputStream inputStreamPOSTagger = KikoBot.class.getResourceAsStream("/models/en-pos-maxent.bin");
        POSModel posModel = new POSModel(inputStreamPOSTagger);
        posTagger = new POSTaggerME(posModel);

        InputStream dictLemmatizer = KikoBot.class.getResourceAsStream("/models/en-lemmatizer.dict");
        lemmatizer = new DictionaryLemmatizer(dictLemmatizer);

        InputStream inputStreamChunker = KikoBot.class.getResourceAsStream("/models/en-chunker.bin");
        ChunkerModel chunkerModel = new ChunkerModel(inputStreamChunker);    
        chunker = new ChunkerME(chunkerModel);
    }



    @Override
    public void onEvent(Event event) {
        // Ignore these events
        if (
            event instanceof StatusChangeEvent
            || event instanceof UserUpdateOnlineStatusEvent
            || event instanceof UserUpdateGameEvent
            || event instanceof HttpRequestEvent
            || event instanceof GenericGuildVoiceEvent
            || event instanceof UserTypingEvent
        ) {
            return;
        }
        System.out.println("EVENT: " + event.getClass().getSimpleName());
        if (event instanceof GenericGuildEvent) {
            Guild guild = ((GenericGuildEvent)event).getGuild();
            if (guild != null) {
                System.out.println("-- GUILD=" + guild.getName());
            }
        }
        if (event instanceof GenericMessageEvent) {
            final GenericMessageEvent msgEvent = (GenericMessageEvent)event;
            System.out.println("-- CHANNEL=" + msgEvent.getChannel().getName());
            Guild guild = ((GenericMessageEvent)event).getGuild();
            if (guild != null) {
                System.out.println("-- GUILD=" + msgEvent.getGuild().getName());
            }
        }
        switch (event.getClass().getSimpleName()) {
            case "ReadyEvent":
                onReady((ReadyEvent)event);
                break;
            case "MessageReceivedEvent":
                onMessageReceived((MessageReceivedEvent)event);
                break;
            // case "GuildMemberJoinEvent":
            // case "GuildMessageReceivedEvent":
        }
    }

    public void onReady(ReadyEvent event) {
        System.out.println("I am ready to go!");
    }

    public void onMessageReceived(MessageReceivedEvent event) {
        try {
            if (event.getAuthor().equals(event.getJDA().getSelfUser())) {
                return;
            }
            System.out.println("-- CHANNEL=" + event.getChannel().getName());
            System.out.println("-- MESSAGE='" + event.getMessage().getContentDisplay() + "'");
            System.out.println("-- AUTHOR=" + event.getAuthor().getName());
            if (event.getMember() != null) {
                System.out.println("-- NICKNAME=" + event.getMember().getNickname());
            }
            System.out.println("-- ISWEBHOOK=" + event.isWebhookMessage());
            if (event.getGuild() != null) {
                System.out.println("-- DEFAULTCHANNEL=" + event.getGuild().getDefaultChannel().getName());
                System.out.println("-- SYSTEMCHANNEL=" + event.getGuild().getSystemChannel().getName());
            }
            switch (event.getChannelType()) {
                case PRIVATE: System.out.println("-- PRIVATE CHANNEL"); break;
                case TEXT: System.out.println("-- TEXT CHANNEL"); break;
                case VOICE: System.out.println("-- VOICE CHANNEL"); break;
                case GROUP: System.out.println("-- GROUP CHANNEL"); break;
                case CATEGORY: System.out.println("-- CATEGORY CHANNEL"); break;
                case UNKNOWN: System.out.println("-- UNKNOWN CHANNEL"); break;
            }
            if (event.getGuild() != null && event.getGuild().getSystemChannel().getName().equals(event.getChannel().getName()) && "".equals(event.getMessage().getContentDisplay())) {
                List<TextChannel> rulesChannels = event.getGuild().getTextChannelsByName("rules", true);
                String rulesChannelId = "";
                for (TextChannel tc : rulesChannels) {
                    if ("rules".equals(tc.getName())) {
                        rulesChannelId = tc.getId();
                        break;
                    }
                }
                final String welcomeMessage = "Welcome to the AnimeFest server " + event.getAuthor().getAsMention() + ". Please make sure you read the <#" + rulesChannelId + ">.";
                event.getChannel().sendMessage(welcomeMessage).queue();
            } else {
                List<String> results = testNLP(event.getMessage().getContentDisplay());
                for (String msg : results) {
                    if (!msg.isEmpty()) {
                        System.out.println("PARSED=" + msg);
                    }
                }
                if (
                    ChannelType.PRIVATE.equals(event.getChannel().getType())
                    || event.getMessage().getMentionedMembers().contains(event.getGuild().getSelfMember())
                ) {
                    for (String msg : results) {
                        if (!msg.isEmpty()) {
                            event.getChannel().sendMessage(msg).queue();
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private List<String> testNLP(String paragraph) {
        final List<String> results = new ArrayList<String>();
        try {
            InputStream is = KikoBot.class.getResourceAsStream("/models/en-sent.bin");
            SentenceModel model = new SentenceModel(is);
            SentenceDetectorME sdetector = new SentenceDetectorME(model);
            String sentences[] = sdetector.sentDetect(paragraph);
            for (String s : sentences) {
                System.out.println("SENTENCE: " + s);
                String[] tokens = tokenizer.tokenize(s);
                String tags[] = posTagger.tag(tokens);
                String[] lemmas = lemmatizer.lemmatize(tokens, tags);
                String[] chunks = chunker.chunk(tokens, tags);

                final StringBuffer buff = new StringBuffer();
                final StringBuffer phraseBuff = new StringBuffer();
                String phrase = "";
                String phraseType = "";
                String sentenceType = "STATEMENT";
                if (s.endsWith("?") || (
                    lemmas.length > 0
                    && (
                        "who".equals(lemmas[0])
                        || "what".equals(lemmas[0])
                        || "where".equals(lemmas[0])
                        || "when".equals(lemmas[0])
                        || "how".equals(lemmas[0])
                    )
                )) {
                    sentenceType = "QUESTION";
                }
                phraseBuff.append(sentenceType);
                for (int i = 0; i < tokens.length; i++) {
                    if (buff.length() > 0) {
                        buff.append(" ");
                    }
                    if (!"O".equals(lemmas[i])) {
                        buff.append(lemmas[i]);
                    } else {
                        buff.append(tokens[i]);
                    }
                    switch (tags[i]) {
                        case "CC": buff.append("(Coordinating conjunction)"); break;
                        case "CD": buff.append("(Cardinal number)"); break;
                        case "DT": buff.append("(Determiner)"); break;
                        case "EX": buff.append("(Existential there)"); break;
                        case "FW": buff.append("(Foreign word)"); break;
                        case "IN": buff.append("(Preposition or subordinating conjunction)"); break;
                        case "JJ": buff.append("(Adjective)"); break;
                        case "JJR": buff.append("(Adjective, comparative)"); break;
                        case "JJS": buff.append("(Adjective, superlative)"); break;
                        case "LS": buff.append("(List item marker)"); break;
                        case "MD": buff.append("(Modal)"); break;
                        case "NN": buff.append("(Noun, singular or mass)"); break;
                        case "NNS": buff.append("(Noun, plural)"); break;
                        case "NNP": buff.append("(Proper noun, singular)"); break;
                        case "NNPS": buff.append("(roper noun, plural)"); break;
                        case "PDT": buff.append("(Predeterminer)"); break;
                        case "POS": buff.append("(Possessive ending)"); break;
                        case "PRP": buff.append("(Personal pronoun)"); break;
                        case "PRP$": buff.append("(Possessive pronoun)"); break;
                        case "RB": buff.append("(Adverb)"); break;
                        case "RBR": buff.append("(Adverb, comparative)"); break;
                        case "RBS": buff.append("(Adverb, superlative)"); break;
                        case "RP": buff.append("(Particle)"); break;
                        case "SYM": buff.append("(Symbol)"); break;
                        case "TO": buff.append("(to)"); break;
                        case "UH": buff.append("(Interjection)"); break;
                        case "VB": buff.append("(Verb, base form)"); break;
                        case "VBD": buff.append("(Verb, past tense)"); break;
                        case "VBG": buff.append("(Verb, gerund or present participle)"); break;
                        case "VBN": buff.append("(Verb, past participle)"); break;
                        case "VBP": buff.append("(Verb, non-3rd person singular present)"); break;
                        case "VBZ": buff.append("(Verb, 3rd person singular present)"); break;
                        case "WDT": buff.append("(Wh-determiner)"); break;
                        case "WP": buff.append("(Wh-pronoun)"); break;
                        case "WP$": buff.append("(Possessive wh-pronoun)"); break;
                        case "WRB": buff.append("(Wh-adverb)"); break;
                    }
                    if (chunks[i].startsWith("B") || "O".equals(chunks[i])) {
                        if (!phrase.isEmpty()) {
                            appendPhrase(phraseBuff, phrase, phraseType);
                        }
                        phrase = "";
                        if (chunks[i].startsWith("B")) {
                            if (!"O".equals(lemmas[i])) {
                                phrase = lemmas[i];
                            } else {
                                phrase = tokens[i];
                            }
                            phraseType = chunks[i].substring(2);
                        }
                    } else {
                        if (!"O".equals(lemmas[i])) {
                            phrase = phrase + " " + lemmas[i];
                        } else {
                            phrase = phrase + " " + tokens[i];
                        }
                    }
                    // System.out.println(tokens[i] + " = " + tags[i] + " lemma=" + lemmas[i] + " chunk=" + chunks[i]);
                }
                if (!phrase.isEmpty()) {
                    appendPhrase(phraseBuff, phrase, phraseType);
                }
                results.add(buff.toString());
                results.add(phraseBuff.toString());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return results;
    }

    private static void appendPhrase(StringBuffer phraseBuff, String phrase, String phraseType) {
        if (phraseBuff.length() > 0) {
            phraseBuff.append(" / ");
        }
        phraseBuff.append(phrase);
        switch (phraseType) {
            case "NP": phraseBuff.append(" (noun)"); break;
            case "VP": phraseBuff.append(" (verb)"); break;
            case "PP": phraseBuff.append(" (preposition)"); break;
            case "ADJP": phraseBuff.append(" (adjective)"); break;
            case "ADVP": phraseBuff.append(" (adverb)"); break;
            case "CONJP": phraseBuff.append(" (conjunction)"); break;
            case "FRAG": phraseBuff.append(" (fragment)"); break;
            case "INTJ": phraseBuff.append(" (interjection)"); break;
            case "LST": phraseBuff.append(" (list-marker)"); break;
            case "NAC": phraseBuff.append(" (not-a-constituent)"); break;
            case "NX": phraseBuff.append(" (complex-noun)"); break;
            case "PRN": phraseBuff.append(" (parenthetical)"); break;
            case "PRT": phraseBuff.append(" (particle)"); break;
            case "QP": phraseBuff.append(" (quantifier)"); break;
            case "RRC": phraseBuff.append(" (reduced-relative-clause)"); break;
            case "UCP": phraseBuff.append(" (unlike-coordinated)"); break;
            default: phraseBuff.append(" (" + phraseType + ")");
        }
    }


 }