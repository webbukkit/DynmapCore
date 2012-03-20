var config = {
	// For internal server or proxying webserver.
	url : {
		configuration: 'up/configuration',
		update: 'up/world/{world}/{timestamp}',
		sendmessage: 'up/sendmessage',
		login: 'up/login',
		register: 'up/register'
	},

	// // For proxying webserver through php.
	// url: {
	// configuration: 'up.php?path=configuration',
	// update: 'up.php?path=world/{world}/{timestamp}',
	// sendmessage: 'up.php?path=sendmessage',
	// login: 'up.php?path=login',
	// register: 'up.php?path=register'
	// },

	// // For proxying webserver through aspx.
	// url: {
	// configuration: 'up.aspx?path=configuration',
	// update: 'up.aspx?path=world/{world}/{timestamp}',
	// sendmessage: 'up.aspx?path=sendmessage',
	// login: 'up.aspx?path=login',
	// register: 'up.aspx?path=register'
	// },

	// // For standalone (jsonfile) webserver (no login security)
	// url: {
	// configuration: 'standalone/dynmap_config.json?_={timestamp}',
	// update: 'standalone/dynmap_{world}.json?_={timestamp}',
	// sendmessage: 'standalone/sendmessage.php',
	// login: 'standalone/login.php',
	// register: 'standalone/register.php'
	// },

	// // For standalone (jsonfile) webserver (login security)
	// url: {
	// configuration: 'standalone/configuration.php',
	// update: 'standalone/dynmap_{world}.json?_={timestamp}',
	// sendmessage: 'standalone/sendmessage.php',
	// login: 'standalone/login.php',
	// register: 'standalone/register.php'
	// },

	tileUrl : 'tiles/',
	tileWidth : 128,
	tileHeight : 128
};
