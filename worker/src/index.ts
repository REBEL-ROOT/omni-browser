export interface Env {
  TELEGRAM_BOT_TOKEN?: string;
  TELEGRAM_CHAT_ID?: string;
  DISCORD_WEBHOOK_URL?: string;
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    // 1. CORS Preflight Handling
    if (request.method === "OPTIONS") {
      return handleCors();
    }

    const url = new URL(request.url);

    // 2. Routing Handlers
    try {
      if (url.pathname === "/api/send-email" && request.method === "POST") {
        return await handleContactSubmission(request, env);
      }

      if (url.pathname === "/api/feedback" && request.method === "POST") {
        return await handleFeedbackSubmission(request, env);
      }

      // Fallback 404
      return new Response(JSON.stringify({ error: "Route not found" }), {
        status: 404,
        headers: getCorsHeaders({ "Content-Type": "application/json" }),
      });
    } catch (err: any) {
      return new Response(JSON.stringify({ error: err.message || "Internal Server Error" }), {
        status: 500,
        headers: getCorsHeaders({ "Content-Type": "application/json" }),
      });
    }
  },
};

/* ─── Route Handlers ─── */

async function handleContactSubmission(request: Request, env: Env): Promise<Response> {
  const payload: any = await request.json();
  const { name, email, description, subject } = payload;

  if (!name || !email || !description) {
    return new Response(JSON.stringify({ error: "Missing required fields: name, email, description" }), {
      status: 400,
      headers: getCorsHeaders({ "Content-Type": "application/json" }),
    });
  }

  // Format content for notifications
  const telegramText = `<b>📬 New Contact Submission</b>\n\n` +
                       `<b>Subject:</b> ${subject || "Contact Form Inquiry"}\n` +
                       `<b>Name:</b> ${name}\n` +
                       `<b>Email:</b> ${email}\n\n` +
                       `<b>Message:</b>\n${description}`;

  const discordEmbed = {
    title: "📬 New Contact Submission",
    color: 3447003, // Hex #3498db (Blue)
    fields: [
      { name: "Subject", value: subject || "Contact Form Inquiry", inline: false },
      { name: "Name", value: name, inline: true },
      { name: "Email", value: email, inline: true },
      { name: "Message", value: description, inline: false },
    ],
    timestamp: new Date().toISOString(),
  };

  await sendNotification(env, telegramText, discordEmbed);

  return new Response(null, {
    status: 204,
    headers: getCorsHeaders(),
  });
}

async function handleFeedbackSubmission(request: Request, env: Env): Promise<Response> {
  const payload: any = await request.json();
  const { name, email, rating, product, message } = payload;

  if (!name || !email || !rating || !product || !message) {
    return new Response(JSON.stringify({ error: "Missing required fields: name, email, rating, product, message" }), {
      status: 400,
      headers: getCorsHeaders({ "Content-Type": "application/json" }),
    });
  }

  const numericRating = parseInt(rating);
  const starRating = "⭐".repeat(Math.min(5, Math.max(1, numericRating)));

  // Format content for notifications
  const telegramText = `<b>⭐️ New Product Feedback</b>\n\n` +
                       `<b>Product:</b> ${product}\n` +
                       `<b>Rating:</b> ${starRating} (${numericRating}/5)\n` +
                       `<b>User:</b> ${name} (${email})\n\n` +
                       `<b>Comment:</b>\n${message}`;

  // Color embed based on user rating (Green for high, Yellow for medium, Red for low)
  const discordColor = numericRating >= 4 ? 3066993 : numericRating <= 2 ? 15158332 : 15844367;

  const discordEmbed = {
    title: `⭐️ New Product Feedback: ${product}`,
    color: discordColor,
    fields: [
      { name: "Rating", value: `${starRating} (${numericRating}/5)`, inline: true },
      { name: "User Name", value: name, inline: true },
      { name: "User Email", value: email, inline: true },
      { name: "Feedback Comment", value: message, inline: false },
    ],
    timestamp: new Date().toISOString(),
  };

  await sendNotification(env, telegramText, discordEmbed);

  return new Response(null, {
    status: 204,
    headers: getCorsHeaders(),
  });
}

/* ─── Notification Dispatcher ─── */

async function sendNotification(env: Env, telegramText: string, discordEmbed: any) {
  const promises: Promise<any>[] = [];

  // Send to Telegram if configured
  if (env.TELEGRAM_BOT_TOKEN && env.TELEGRAM_CHAT_ID) {
    const telegramUrl = `https://api.telegram.org/bot${env.TELEGRAM_BOT_TOKEN}/sendMessage`;
    const promise = fetch(telegramUrl, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        chat_id: env.TELEGRAM_CHAT_ID,
        text: telegramText,
        parse_mode: "HTML",
      }),
    }).then(async (res) => {
      if (!res.ok) {
        console.error("Telegram API Error:", await res.text());
      }
    });
    promises.push(promise);
  }

  // Send to Discord if configured
  if (env.DISCORD_WEBHOOK_URL) {
    const promise = fetch(env.DISCORD_WEBHOOK_URL, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        embeds: [discordEmbed],
      }),
    }).then(async (res) => {
      if (!res.ok) {
        console.error("Discord Webhook Error:", await res.text());
      }
    });
    promises.push(promise);
  }

  if (promises.length === 0) {
    console.warn("No notification channels (Telegram or Discord) are configured in the environment.");
  } else {
    await Promise.all(promises);
  }
}

/* ─── CORS Helpers ─── */

function handleCors(): Response {
  return new Response(null, {
    status: 204,
    headers: getCorsHeaders(),
  });
}

function getCorsHeaders(customHeaders: Record<string, string> = {}): Headers {
  const headers = new Headers({
    "Access-Control-Allow-Origin": "*",
    "Access-Control-Allow-Methods": "POST, OPTIONS",
    "Access-Control-Allow-Headers": "Content-Type, Authorization",
    "Access-Control-Max-Age": "86400",
    ...customHeaders,
  });
  return headers;
}
