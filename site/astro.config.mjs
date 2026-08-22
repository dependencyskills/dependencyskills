// @ts-check
import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';

// https://astro.build/config
export default defineConfig({
	// Set `site` to the published URL once the domain is confirmed — it enables
	// the sitemap and canonical links.
	// site: 'https://<your-domain>',
	integrations: [
		starlight({
			title: 'Dependency Skills',
			customCss: ['./src/styles/custom.css'],
			components: {
				Footer: './src/components/Footer.astro',
			},
			social: [
				{ icon: 'github', label: 'GitHub', href: 'https://github.com/dependencyskills/dependencyskills' },
			],
			sidebar: [
				{ label: 'Overview', link: '/' },
				{ label: 'Adopted Standards', link: '/standards/' },
				{
					label: 'The case',
					items: [
						{ label: 'The findings', link: '/findings/' },
						{ label: 'What the tests show', link: '/experiments/' },
						{ label: 'The field as it stands', link: '/field/' },
						{ label: 'The landscape', link: '/landscape/' },
					],
				},
				{
					label: 'Case studies',
					items: [
						{ label: 'Thirteen slug functions', link: '/case-studies/thirteen-slug-functions/' },
						{ label: 'The datetime Instant that moved', link: '/case-studies/the-datetime-instant-move/' },
						{ label: 'The legacy library everyone remembers', link: '/case-studies/the-legacy-library-everyone-remembers/' },
						{ label: 'The dependency nobody declared', link: '/case-studies/the-dependency-nobody-declared/' },
					],
				},
				{
					label: 'Where it stands',
					items: [
						{ label: 'Decisions', link: '/decisions/' },
						{ label: 'The research', link: '/research/' },
					],
				},
				{
					label: 'How we work',
					items: [
						{ label: 'Methodology & tools', link: '/methodology/' },
					],
				},
			],
		}),
	],
});
